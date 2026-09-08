package com.vircas.mobile.ui

import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.core.game.CrashBlast
import com.vircas.mobile.core.game.CrashFlight
import com.vircas.mobile.core.game.WagerRules
import com.vircas.mobile.game.engines.CrashRound
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal enum class CrashPhase { READY, PREPARING, FLYING, CRASHED, RESETTING }

internal data class CrashUiState(
    val phase: CrashPhase = CrashPhase.READY,
    val stakeText: String = "1000",
    val stake: Long = 0L,
    val flight: CrashFlight? = null,
    val blastFlight: CrashFlight? = null,
    val revealStartedAtNanos: Long? = null,
    val settling: Boolean = false,
    val pendingCashout: Double? = null,
    val collectedAt: Double? = null,
    val payout: Long = 0L,
    val leaving: Boolean = false,
    val exitReady: Boolean = false,
    val recentCrashes: List<Double> = emptyList(),
    val message: String = "Set your stake. The result is fixed before launch."
) {
    val canEditStake: Boolean get() =
        phase == CrashPhase.READY && blastFlight == null && !settling && !leaving
}

/** Testable adapter: the real implementation delegates every write to AppViewModel's FIFO. */
internal interface CrashRoundPort {
    fun reserve(stake: Long): Deferred<ActiveWager?>
    fun newRound(wager: ActiveWager): CrashRound
    fun checkpoint(wager: ActiveWager, multiplier: Double, result: String, details: String, terminal: Boolean): Deferred<Unit>
    fun settle(wager: ActiveWager, multiplier: Double, result: String, details: String): Deferred<Unit>
    fun cancel(wager: ActiveWager): Deferred<Unit>
}

/** Main-dispatcher controller retained in RoundMemory, never in a screen's LaunchedEffect. */
internal class CrashRoundController(
    private val scope: CoroutineScope,
    private val port: CrashRoundPort,
    private val nowNanos: () -> Long
) {
    private val mutableState = MutableStateFlow(CrashUiState())
    val state: StateFlow<CrashUiState> = mutableState
    private var wager: ActiveWager? = null
    private var armed = false
    private var preparation: Job? = null
    private var settlement: Job? = null
    private var deadline: Job? = null
    private var sceneReset: Job? = null

    fun changeStake(text: String) {
        if (state.value.canEditStake && text.length <= 15 && text.all { it in '0'..'9' }) {
            mutableState.value = state.value.copy(stakeText = text)
        }
    }

    fun start(balance: Long) {
        val current = state.value
        if (!current.canEditStake || wager != null) return
        val stake = current.stakeText.toLongOrNull()
        if (stake == null || stake <= 0L || stake > balance) {
            mutableState.value = current.copy(message = "Enter a stake within your virtual balance.")
            return
        }
        deadline?.cancel()
        sceneReset?.cancel()
        armed = false
        mutableState.value = current.copy(
            phase = CrashPhase.PREPARING, stake = stake, flight = null,
            blastFlight = null, revealStartedAtNanos = null, settling = false, pendingCashout = null, collectedAt = null, payout = 0L,
            message = "Saving your round before launch…"
        )
        preparation = scope.launch {
            try {
                val reserved = port.reserve(stake).await()
                if (reserved == null) {
                    mutableState.value = state.value.copy(phase = CrashPhase.READY, message = "Could not reserve this stake. Try again.")
                    return@launch
                }
                wager = reserved
                if (state.value.leaving) {
                    refundBeforeLaunch(reserved)
                    return@launch
                }
                val round = port.newRound(reserved)
                // Nonterminal: recovery loses a launched, uncollected bet, but a real cashout can replace it.
                port.checkpoint(reserved, 0.0, "Crash interrupted", "Launched without a confirmed cashout", false).await()
                armed = true
                if (state.value.leaving) {
                    refundBeforeLaunch(reserved)
                    return@launch
                }
                val flight = CrashFlight(nowNanos(), round.crashPoint)
                mutableState.value = state.value.copy(phase = CrashPhase.FLYING, flight = flight, message = "Flight live. Cash out before the crash.")
                tick(nowNanos()) // A 1.00x round is already lost; never offer a winning first-frame tap.
                if (state.value.phase == CrashPhase.FLYING) {
                    deadline = scope.launch {
                        while (state.value.flight === flight && state.value.phase == CrashPhase.FLYING) {
                            val remaining = flight.durationSeconds - flight.elapsedSeconds(nowNanos())
                            if (remaining <= 0.0) {
                                tick(nowNanos())
                                break
                            }
                            delay(ceil(remaining * 1000.0).toLong().coerceAtLeast(1L))
                            tick(nowNanos())
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the reservation reachable. Back can cancel an unlaunched round; never silently retry RNG.
                mutableState.value = state.value.copy(
                    phase = if (wager == null) CrashPhase.READY else state.value.phase,
                    message = if (wager == null) "Could not launch. Try again." else "Launch interrupted. Go back to close this round."
                )
            }
        }
    }

    fun tick(atNanos: Long) {
        val current = state.value
        val flight = current.flight ?: return
        if (current.phase != CrashPhase.FLYING || !flight.hasCrashed(atNanos)) return
        mutableState.value = current.copy(
            phase = CrashPhase.CRASHED, blastFlight = flight, revealStartedAtNanos = null,
            recentCrashes = (listOf(flight.crashPoint) + current.recentCrashes).take(8),
            message = when {
                current.settling -> "Saving the result…"
                current.collectedAt != null -> "Flight ended. Your cashout is safe."
                else -> "Signal lost at ${crashMultiplierText(flight.crashPoint)}."
            }
        )
        val active = wager
        if (active != null && !current.settling) {
            finish(active, 0.0, "Crash @ ${crashMultiplierText(flight.crashPoint)}", "No cashout before the crash", false)
        }
        scheduleSceneReset(flight)
    }

    private fun scheduleSceneReset(flight: CrashFlight) {
        sceneReset?.cancel()
        sceneReset = scope.launch {
            awaitClock(flight.startedAtNanos, flight.durationSeconds + CrashBlast.COVER_SECONDS)
            // The reset is visual only. Never unlock Start merely because the effect finished.
            settlement?.join()
            if (state.value.blastFlight !== flight || wager != null || state.value.settling) return@launch
            val revealStarted = nowNanos()
            val summary = if (state.value.collectedAt != null) "Cashout saved. Ready for a new flight." else "Round ended. Ready for a new flight."
            mutableState.value = state.value.copy(
                phase = CrashPhase.RESETTING, flight = null, stake = 0L,
                collectedAt = null, pendingCashout = null, payout = 0L,
                revealStartedAtNanos = revealStarted, message = summary
            )
            awaitClock(revealStarted, CrashBlast.REVEAL_SECONDS)
            if (state.value.blastFlight === flight) {
                mutableState.value = state.value.copy(phase = CrashPhase.READY, blastFlight = null, revealStartedAtNanos = null)
            }
        }
    }

    private suspend fun awaitClock(startedAtNanos: Long, seconds: Double) {
        while (true) {
            val remaining = seconds - (nowNanos() - startedAtNanos).coerceAtLeast(0L) / 1_000_000_000.0
            if (remaining <= 0.0) return
            delay(ceil(remaining * 1000.0).toLong().coerceAtLeast(1L))
        }
    }

    fun collect(atNanos: Long = nowNanos()) {
        val current = state.value
        val active = wager ?: return
        val flight = current.flight ?: return
        if (current.phase != CrashPhase.FLYING || current.settling || current.leaving) return
        val multiplier = flight.cashoutMultiplier(atNanos)
        if (multiplier == null) {
            tick(atNanos)
            return
        }
        finish(active, multiplier, "Collected @ ${crashMultiplierText(multiplier)}", "Cashout sampled from monotonic tap time", true)
    }

    private fun finish(active: ActiveWager, multiplier: Double, result: String, details: String, collected: Boolean) {
        // Set synchronously, before launching a coroutine: repeated taps / the deadline cannot enqueue another result.
        mutableState.value = state.value.copy(
            settling = true, pendingCashout = if (collected) multiplier else null,
            message = if (collected) "Saving your cashout…" else "Saving the round…"
        )
        val confirmedCheckpoint = port.checkpoint(active, multiplier, result, details, true)
        settlement = scope.launch {
            confirmedCheckpoint.await()
            port.settle(active, multiplier, result, details).await()
            wager = null
            mutableState.value = state.value.copy(
                settling = false, pendingCashout = null,
                collectedAt = if (collected) multiplier else null,
                payout = if (collected) WagerRules.payout(active.stake, multiplier) else 0L,
                message = when {
                    collected && state.value.phase == CrashPhase.FLYING -> "Collected. Watch the rest of the flight."
                    collected -> "Flight ended. Your cashout is safe."
                    else -> "Round ended. Ready when you are."
                }
            )
        }
    }

    fun requestLeave() {
        if (state.value.leaving) return
        // Back is a current-time cashout, not a refund and not the last displayed multiplier.
        if (state.value.phase == CrashPhase.FLYING) collect(nowNanos())
        mutableState.value = state.value.copy(leaving = true)
        scope.launch {
            preparation?.join()
            settlement?.join()
            val unlaunched = wager
            if (unlaunched != null && state.value.flight == null) refundBeforeLaunch(unlaunched)
            mutableState.value = state.value.copy(exitReady = true)
        }
    }

    private suspend fun refundBeforeLaunch(active: ActiveWager) {
        // A queued 0x checkpoint might already be on disk; only an unlaunched reservation can be refunded.
        if (armed) port.settle(active, 1.0, "Crash launch cancelled", "Left before the monotonic flight clock started").await()
        else port.cancel(active).await()
        wager = null
        mutableState.value = state.value.copy(phase = CrashPhase.READY, message = "Launch cancelled.")
    }

    fun consumeExit() {
        val current = state.value
        if (current.blastFlight != null && wager == null && !current.settling) {
            sceneReset?.cancel()
            mutableState.value = current.copy(
                phase = CrashPhase.READY, flight = null, blastFlight = null, revealStartedAtNanos = null,
                stake = 0L, collectedAt = null, pendingCashout = null, payout = 0L,
                leaving = false, exitReady = false
            )
        } else mutableState.value = current.copy(leaving = false, exitReady = false)
    }
}

internal fun crashMultiplierText(multiplier: Double): String = String.format(Locale.US, "%.2fx", multiplier)
