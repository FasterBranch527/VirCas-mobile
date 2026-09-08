package com.vircas.mobile.core.game

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FIFO for local round writes. Confine calls to the owner's dispatcher (Main in the VM).
 * A failed write stays at the head, and its acknowledgement stays pending until Retry succeeds.
 * Cancelling an observer never cancels a transaction or allows a later write to overtake it.
 */
internal class RoundWriteQueue(
    private val scope: CoroutineScope,
    private val onFailure: (Exception) -> Unit,
    private val onIdle: () -> Unit
) {
    private class Write(
        val generation: Int,
        val execute: suspend () -> (() -> Unit),
        val cancel: () -> Unit
    )

    private val pending = ArrayDeque<Write>()
    private var generation = 0
    private var worker: Job? = null
    private var failed = false

    fun <T> submit(block: suspend () -> T): Deferred<T> {
        // Deliberately not a child of an individual screen/animation job.
        val acknowledgement = CompletableDeferred<T>()
        check(scope.isActive) { "Round writer is closed" }
        pending.addLast(Write(
            generation = generation,
            execute = {
                val result = block()
                val confirm: () -> Unit = { acknowledgement.complete(result); Unit }
                confirm
            },
            cancel = { acknowledgement.cancel() }
        ))
        start()
        return acknowledgement
    }

    fun retry() {
        failed = false
        start()
    }

    /** Reset invalidates old observers, but an in-flight transaction must finish before reset. */
    fun discardPending() {
        generation++
        pending.forEach { it.cancel() }
        pending.clear()
        failed = false
    }

    private fun start() {
        if (worker != null || failed || !scope.isActive) return
        val nextWorker = scope.launch(start = CoroutineStart.LAZY) {
            try {
                while (pending.isNotEmpty()) {
                    val write = pending.first()
                    try {
                        val confirm = withContext(NonCancellable) { write.execute() }
                        if (!scope.isActive) break
                        if (write.generation == generation) {
                            check(pending.removeFirst() === write)
                            confirm()
                        }
                    } catch (error: Exception) {
                        if (!scope.isActive) throw error
                        if (write.generation == generation) {
                            failed = true
                            onFailure(error)
                            break
                        }
                        // A reset invalidated this transaction while it was in flight.
                        // Continue with the queued reset; do not surface a stale error.
                    }
                }
                if (pending.isEmpty()) onIdle()
            } finally {
                worker = null
                if (!scope.isActive) {
                    pending.forEach { it.cancel() }
                    pending.clear()
                } else if (pending.isNotEmpty() && !failed) {
                    start()
                }
            }
        }
        worker = nextWorker
        nextWorker.start()
    }
}
