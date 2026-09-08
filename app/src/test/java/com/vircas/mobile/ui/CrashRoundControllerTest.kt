package com.vircas.mobile.ui

import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.core.game.CrashFlightMath
import com.vircas.mobile.core.game.WagerRecord
import com.vircas.mobile.core.game.WagerRules
import com.vircas.mobile.game.engines.CrashRound
import kotlin.math.ceil
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
class CrashRoundControllerTest {
    @Test fun launchWaitsForDurableFallbackAndIgnoresDoubleStart() = runTest {
        val fixture = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        fixture.port.checkpointGate = gate
        fixture.controller.start(10_000L)
        fixture.controller.start(10_000L)
        runCurrent()
        assertEquals(1, fixture.port.reservations)
        assertEquals(CrashPhase.PREPARING, fixture.controller.state.value.phase)
        assertNull(fixture.controller.state.value.flight)
        gate.complete(Unit)
        runCurrent()
        assertEquals(CrashPhase.FLYING, fixture.controller.state.value.phase)
        assertEquals(0.0, requireNotNull(fixture.port.record?.checkpointMultiplier), 0.0)
        assertFalse(requireNotNull(fixture.port.record).checkpointTerminal)
        assertEquals(1, fixture.port.generatedRounds)
    }

    @Test fun cashoutUsesTapTimeEvenWithoutAnyRenderedFrame() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start(10_000L)
        runCurrent()
        fixture.now = 2_000_000_000L
        fixture.controller.collect()
        runCurrent()
        val expected = CrashFlightMath.multiplier(2.0)
        assertEquals(expected, requireNotNull(fixture.controller.state.value.collectedAt), 1e-12)
        assertEquals(WagerRules.payout(1000L, expected), fixture.controller.state.value.payout)
        assertEquals(1, fixture.port.settlements.size)
    }

    @Test fun duplicateCashoutAndDeadlineCannotOvertakePendingCheckpoint() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start(10_000L)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        fixture.port.checkpointGate = gate
        fixture.now = 1_000_000_000L
        fixture.controller.collect()
        fixture.controller.collect()
        runCurrent()
        assertTrue(fixture.controller.state.value.settling)
        assertNull(fixture.controller.state.value.collectedAt)
        val flight = requireNotNull(fixture.controller.state.value.flight)
        fixture.now = flight.durationNanos + 1L
        fixture.controller.tick(fixture.now)
        fixture.controller.start(10_000L)
        runCurrent()
        assertEquals(CrashPhase.CRASHED, fixture.controller.state.value.phase)
        assertEquals(2, fixture.port.checkpoints) // fallback + one winning result
        assertTrue(fixture.port.settlements.isEmpty())
        assertEquals(1, fixture.port.reservations)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, fixture.port.settlements.size)
        assertNotNull(fixture.controller.state.value.collectedAt)
        assertFalse(fixture.controller.state.value.settling)
    }

    @Test fun collectedLabelAndNextRoundWaitForSettlementAcknowledgement() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start(10_000L)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        fixture.port.settlementGate = gate
        fixture.now = 1_000_000_000L
        fixture.controller.collect()
        runCurrent()
        assertTrue(fixture.controller.state.value.settling)
        assertNull(fixture.controller.state.value.collectedAt)
        assertFalse(fixture.controller.state.value.canEditStake)
        gate.complete(Unit)
        runCurrent()
        assertFalse(fixture.controller.state.value.settling)
        assertNotNull(fixture.controller.state.value.collectedAt)
        assertEquals(WagerRecord.SETTLED, fixture.port.record?.status)
    }

    @Test fun cashoutAtDeadlineIsALossNotARefund() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start(10_000L)
        runCurrent()
        fixture.now = requireNotNull(fixture.controller.state.value.flight).durationNanos
        fixture.controller.collect()
        runCurrent()
        assertEquals(CrashPhase.CRASHED, fixture.controller.state.value.phase)
        assertNull(fixture.controller.state.value.collectedAt)
        assertEquals(0.0, fixture.port.settlements.single(), 0.0)
        assertEquals(9000L, fixture.port.balance)
        assertEquals(0, fixture.port.cancellations)
    }

    @Test fun instantCrashCannotPayEvenBeforeAFrame() = runTest {
        val fixture = Fixture(backgroundScope, 1.0)
        fixture.controller.start(10_000L)
        runCurrent()
        fixture.controller.collect()
        runCurrent()
        assertEquals(CrashPhase.CRASHED, fixture.controller.state.value.phase)
        assertEquals(0.0, fixture.port.settlements.single(), 0.0)
        assertEquals(9000L, fixture.port.balance)
    }

    @Test fun backSamplesCurrentTimeAndWaitsForSaving() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start(10_000L)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        fixture.port.settlementGate = gate
        fixture.now = 2_000_000_000L
        fixture.controller.requestLeave()
        fixture.controller.requestLeave()
        runCurrent()
        assertTrue(fixture.controller.state.value.leaving)
        assertFalse(fixture.controller.state.value.exitReady)
        gate.complete(Unit)
        runCurrent()
        assertTrue(fixture.controller.state.value.exitReady)
        assertEquals(CrashFlightMath.multiplier(2.0), fixture.port.settlements.single(), 1e-12)
        assertEquals(0, fixture.port.cancellations)
    }

    @Test fun backWhilePreparingRefundsOnlyTheUnlaunchedReservation() = runTest {
        val fixture = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        fixture.port.checkpointGate = gate
        fixture.controller.start(10_000L)
        runCurrent()
        fixture.controller.requestLeave()
        runCurrent()
        assertFalse(fixture.controller.state.value.exitReady)
        gate.complete(Unit)
        runCurrent()
        assertNull(fixture.controller.state.value.flight)
        assertEquals(10_000L, fixture.port.balance)
        assertEquals(1.0, fixture.port.settlements.single(), 0.0)
        assertTrue(fixture.controller.state.value.exitReady)
    }

    @Test fun deadlineClosesRoundWithoutAScreenOrFrameLoop() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start(10_000L)
        runCurrent()
        val flight = requireNotNull(fixture.controller.state.value.flight)
        fixture.now = flight.durationNanos + 10_000_000L
        advanceTimeBy(ceil(flight.durationSeconds * 1000.0).toLong() + 1L)
        runCurrent()
        assertEquals(CrashPhase.CRASHED, fixture.controller.state.value.phase)
        assertEquals(1, fixture.port.settlements.size)
        assertEquals(1, fixture.controller.state.value.recentCrashes.size)
    }

    @Test fun confirmedCashoutCannotBeSettledTwiceByALateFrame() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.controller.start(10_000L)
        runCurrent()
        fixture.now = 1_000_000_000L
        fixture.controller.collect()
        runCurrent()
        val balance = fixture.port.balance
        fixture.now = requireNotNull(fixture.controller.state.value.flight).durationNanos + 1L
        repeat(4) { fixture.controller.tick(fixture.now) }
        runCurrent()
        assertEquals(balance, fixture.port.balance)
        assertEquals(1, fixture.port.settlements.size)
        assertEquals(1, fixture.controller.state.value.recentCrashes.size)
        assertNotNull(fixture.controller.state.value.collectedAt)
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
        var checkpoints = 0
        var cancellations = 0
        val settlements = mutableListOf<Double>()
        var checkpointGate: CompletableDeferred<Unit>? = null
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
        override fun checkpoint(wager: ActiveWager, multiplier: Double, result: String, details: String, terminal: Boolean) = run {
            checkpoints++
            val gate = checkpointGate
            scope.async {
                gate?.await()
                record = WagerRules.checkpoint(requireNotNull(record), wager, multiplier, result, details, terminal)
            }
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
