package com.vircas.mobile.game

import com.vircas.mobile.core.game.RoundWriteQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoundWriteQueueTest {
    @Test fun failedCheckpointDoesNotAcknowledgeOrPermitSettlement() = runTest {
        val events = mutableListOf<String>()
        var writable = false
        var failures = 0
        val queue = RoundWriteQueue(this, { failures++ }, {})
        val checkpoint = queue.submit {
            if (!writable) error("disk full")
            events += "checkpoint"
        }
        val settlement = queue.submit { events += "settlement" }
        var revealed = false
        val observer = launch { checkpoint.await(); revealed = true }
        runCurrent()
        assertFalse(checkpoint.isCompleted)
        assertFalse(settlement.isCompleted)
        assertFalse(revealed)
        assertEquals(1, failures)
        assertTrue(events.isEmpty())
        writable = true
        queue.retry()
        runCurrent()
        checkpoint.await()
        settlement.await()
        observer.join()
        assertTrue(revealed)
        assertEquals(listOf("checkpoint", "settlement"), events)
    }

    @Test fun repeatedCheckpointsAreNotReplacedByTheSameWagerKey() = runTest {
        var writable = false
        val saved = mutableListOf<String>()
        val queue = RoundWriteQueue(this, {}, {})
        val first = queue.submit { check(writable); saved += "bank" }
        runCurrent()
        val second = queue.submit { saved += "loss" }
        val cancel = queue.submit { saved += "cancel consumes loss" }
        runCurrent()
        assertTrue(saved.isEmpty())
        writable = true
        queue.retry()
        runCurrent()
        first.await(); second.await(); cancel.await()
        assertEquals(listOf("bank", "loss", "cancel consumes loss"), saved)
    }

    @Test fun repeatedRetryDoesNotDuplicateACommittedWrite() = runTest {
        var writable = false
        var attempts = 0
        var commits = 0
        val queue = RoundWriteQueue(this, {}, {})
        val ticket = queue.submit { attempts++; check(writable); commits++ }
        runCurrent()
        queue.retry(); queue.retry()
        runCurrent()
        assertFalse(ticket.isCompleted)
        assertEquals(2, attempts)
        writable = true
        queue.retry(); queue.retry()
        runCurrent()
        queue.retry()
        runCurrent()
        ticket.await()
        assertEquals(3, attempts)
        assertEquals(1, commits)
    }

    @Test fun cancellingAnAnimationDoesNotCancelItsWrite() = runTest {
        val release = CompletableDeferred<Unit>()
        var committed = false
        val queue = RoundWriteQueue(this, {}, {})
        val ticket = queue.submit { release.await(); committed = true }
        val animation = launch { ticket.await() }
        runCurrent()
        animation.cancel()
        release.complete(Unit)
        runCurrent()
        ticket.await()
        assertTrue(committed)
    }

    @Test fun cancellingTicketDoesNotLetALaterWriteOvertakeTransaction() = runTest {
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val queue = RoundWriteQueue(this, {}, {})
        val ticket = queue.submit { release.await(); events += "checkpoint" }
        runCurrent()
        ticket.cancel()
        val next = queue.submit { events += "settle" }
        runCurrent()
        assertTrue(events.isEmpty())
        release.complete(Unit)
        runCurrent()
        next.await()
        assertEquals(listOf("checkpoint", "settle"), events)
    }

    @Test fun resetDiscardsFailedOldRoundAndItsWaitingObservers() = runTest {
        var obsoleteSettlement = false
        var reset = false
        val queue = RoundWriteQueue(this, {}, {})
        val checkpoint = queue.submit { error("disk full") }
        val settlement = queue.submit { obsoleteSettlement = true }
        runCurrent()
        queue.discardPending()
        val resetTicket = queue.submit { reset = true }
        runCurrent()
        resetTicket.await()
        assertTrue(checkpoint.isCancelled)
        assertTrue(settlement.isCancelled)
        assertFalse(obsoleteSettlement)
        assertTrue(reset)
    }

    @Test fun resetWaitsForInFlightTransactionAndSuppressesItsStaleFailure() = runTest {
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var failures = 0
        val queue = RoundWriteQueue(this, { failures++ }, {})
        val old = queue.submit {
            events += "old start"
            release.await()
            events += "old finish"
            error("stale error")
        }
        runCurrent()
        queue.discardPending()
        val reset = queue.submit { events += "reset" }
        runCurrent()
        assertEquals(listOf("old start"), events)
        release.complete(Unit)
        runCurrent()
        reset.await()
        assertTrue(old.isCancelled)
        assertEquals(0, failures)
        assertEquals(listOf("old start", "old finish", "reset"), events)
    }

    @Test fun queueStaysBlockedIfRetryAlsoFails() = runTest {
        var failures = 0
        var idle = 0
        val queue = RoundWriteQueue(this, { failures++ }, { idle++ })
        val ticket = queue.submit { error("still full") }
        runCurrent()
        queue.retry()
        runCurrent()
        assertEquals(2, failures)
        assertEquals(0, idle)
        assertFalse(ticket.isCompleted)
        queue.discardPending()
        queue.retry()
        runCurrent()
        assertTrue(ticket.isCancelled)
        assertEquals(1, idle)
    }

    @Test fun closingOwnerCancelsOutstandingAcknowledgementsAfterTransaction() = runTest {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        val release = CompletableDeferred<Unit>()
        val queue = RoundWriteQueue(owner, {}, {})
        val first = queue.submit { release.await() }
        val next = queue.submit { Unit }
        runCurrent()
        owner.cancel()
        release.complete(Unit)
        runCurrent()
        assertTrue(first.isCancelled)
        assertTrue(next.isCancelled)
    }
}
