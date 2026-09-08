package com.vircas.mobile.ui

import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.core.game.CrashBlast
import com.vircas.mobile.core.game.WagerRecord
import com.vircas.mobile.core.game.WagerRules
import com.vircas.mobile.game.engines.CrashRound
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CrashSceneResetTest {
    @Test fun blastLocksInputsThenClearsOnlyTheScene() = runTest {
        val fixture = Fixture(backgroundScope, 1.0)
        fixture.controller.start(10_000L)
        runCurrent()
        val oldFlight = fixture.controller.state.value.flight
        fixture.controller.start(10_000L)
        fixture.controller.changeStake("5000")
        assertEquals("1000", fixture.controller.state.value.stakeText)
        assertFalse(fixture.controller.state.value.canEditStake)
        fixture.now = 1_000_000_000L
        advanceTimeBy(1000L)
        runCurrent()
        assertEquals(CrashPhase.CRASHED, fixture.controller.state.value.phase)
        fixture.now = 1_600_000_000L
        advanceTimeBy(600L)
        runCurrent()
        assertEquals(CrashPhase.RESETTING, fixture.controller.state.value.phase)
        assertNull(fixture.controller.state.value.flight)
        assertSame(oldFlight, fixture.controller.state.value.blastFlight)
        assertFalse(fixture.controller.state.value.canEditStake)
        fixture.now = 2_000_000_000L
        advanceTimeBy(400L)
        runCurrent()
        assertEquals(CrashPhase.RESETTING, fixture.controller.state.value.phase)
        fixture.controller.start(10_000L)
        fixture.now = 2_500_000_000L
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(CrashPhase.READY, fixture.controller.state.value.phase)
        assertNull(fixture.controller.state.value.blastFlight)
        assertTrue(fixture.controller.state.value.canEditStake)
        assertEquals(listOf(1.0), fixture.controller.state.value.recentCrashes)
        assertEquals(9000L, fixture.port.balance)
        assertEquals(1, fixture.port.reservations)
        assertEquals(1, fixture.port.generatedRounds)
        assertEquals(1, fixture.port.settlements.size)
    }

    @Test fun longSaveHoldsWipeAndStartsRevealOnlyAfterAcknowledgement() = runTest {
        val fixture = Fixture(backgroundScope, 1.0)
        val gate = CompletableDeferred<Unit>()
        fixture.port.settlementGate = gate
        fixture.controller.start(10_000L)
        runCurrent()
        fixture.now = 5_000_000_000L
        advanceTimeBy(5000L)
        runCurrent()
        assertEquals(CrashPhase.CRASHED, fixture.controller.state.value.phase)
        assertNull(fixture.controller.state.value.revealStartedAtNanos)
        assertFalse(fixture.controller.state.value.canEditStake)
        gate.complete(Unit)
        runCurrent()
        assertEquals(CrashPhase.RESETTING, fixture.controller.state.value.phase)
        assertEquals(1f, CrashBlast.overlayAlpha(fixture.controller.state.value.revealStartedAtNanos, fixture.now), 0f)
        fixture.now = 5_900_000_000L
        advanceTimeBy(900L)
        runCurrent()
        assertEquals(CrashPhase.READY, fixture.controller.state.value.phase)
        assertEquals(9000L, fixture.port.balance)
        assertEquals(1, fixture.port.settlements.size)
    }

    @Test fun automaticSceneResetPreservesAnAlreadyPaidCashout() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start(10_000L)
        runCurrent()
        fixture.now = 1_000_000_000L
        fixture.controller.collect()
        runCurrent()
        val paidBalance = fixture.port.balance
        val flight = requireNotNull(fixture.controller.state.value.flight)
        fixture.now = flight.durationNanos + 1L
        fixture.controller.tick(fixture.now)
        runCurrent()
        fixture.now = flight.durationNanos + 1_600_000_000L
        advanceTimeBy(1600L)
        runCurrent()
        fixture.now += 900_000_000L
        advanceTimeBy(900L)
        runCurrent()
        assertEquals(CrashPhase.READY, fixture.controller.state.value.phase)
        assertEquals(paidBalance, fixture.port.balance)
        assertEquals(listOf(3.0), fixture.controller.state.value.recentCrashes)
        assertEquals(1, fixture.port.settlements.size)
        assertEquals(1, fixture.port.reservations)
    }

    @Test fun leavingDuringDestructionCancelsOnlyThePresentation() = runTest {
        val fixture = Fixture(backgroundScope, 1.0)
        fixture.controller.start(10_000L)
        runCurrent()
        fixture.controller.requestLeave()
        runCurrent()
        assertTrue(fixture.controller.state.value.exitReady)
        fixture.controller.consumeExit()
        assertEquals(CrashPhase.READY, fixture.controller.state.value.phase)
        assertNull(fixture.controller.state.value.blastFlight)
        assertEquals(9000L, fixture.port.balance)
        assertEquals(1, fixture.port.settlements.size)
        assertEquals(0, fixture.port.cancellations)
    }

    private class Fixture(scope: CoroutineScope, crashPoint: Double = 3.0) {
        var now = 0L
        val port = FakePort(scope, crashPoint)
        val controller = CrashRoundController(scope, port) { now }
    }

    private class FakePort(private val scope: CoroutineScope, private val crashPoint: Double) : CrashRoundPort {
        var balance = 10_000L
        var record: WagerRecord? = null
        var reservations = 0
        var generatedRounds = 0
        var cancellations = 0
        val settlements = mutableListOf<Double>()
        var settlementGate: CompletableDeferred<Unit>? = null

        override fun reserve(stake: Long) = scope.async<ActiveWager?> {
            reservations++
            balance -= stake
            WagerRecord("crash-$reservations", "Crash", stake, 0L, "seed", "client").also { record = it }.active()
        }
        override fun newRound(wager: ActiveWager): CrashRound {
            generatedRounds++
            return CrashRound(crashPoint, .5)
        }
        override fun checkpoint(wager: ActiveWager, multiplier: Double, result: String, details: String, terminal: Boolean) = scope.async {
            record = WagerRules.checkpoint(requireNotNull(record), wager, multiplier, result, details, terminal)
        }
        override fun settle(wager: ActiveWager, multiplier: Double, result: String, details: String) = run {
            settlements += multiplier
            val gate = settlementGate
            scope.async {
                gate?.await()
                val change = WagerRules.settle(requireNotNull(record), wager, balance, multiplier, result, details, 1L)
                record = change.record
                balance = change.balance
            }
        }
        override fun cancel(wager: ActiveWager) = scope.async {
            cancellations++
            val change = WagerRules.cancel(requireNotNull(record), wager, balance, 1L)
            record = change.record
            balance = change.balance
        }
    }
}
