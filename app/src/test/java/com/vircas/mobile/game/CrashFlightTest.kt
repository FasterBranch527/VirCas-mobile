package com.vircas.mobile.game

import com.vircas.mobile.core.game.CrashFlight
import com.vircas.mobile.core.game.CrashFlightMath
import com.vircas.mobile.core.game.CrashTrajectory
import com.vircas.mobile.core.game.WagerRecord
import com.vircas.mobile.core.game.WagerRules
import kotlin.math.atan2
import kotlin.math.exp
import org.junit.Assert.*
import org.junit.Test

class CrashFlightTest {
    @Test fun originalGrowthAndCapAreUnchanged() {
        for (step in 0..400) {
            val seconds = step / 10.0
            val original = exp(.105 * seconds + .0045 * seconds * seconds).coerceIn(1.0, 1000.0)
            assertEquals(original, CrashFlightMath.multiplier(seconds), 1e-12)
        }
        assertEquals(1.0, CrashFlightMath.multiplier(-5.0), 0.0)
        assertEquals(1000.0, CrashFlightMath.multiplier(10_000.0), 0.0)
    }

    @Test fun inverseMatchesGrowthAcrossTheEntireEngineRange() {
        listOf(1.0, 1.01, 1.25, 2.0, 9.99, 100.0, 999.99, 1000.0).forEach { multiplier ->
            assertEquals(multiplier, CrashFlightMath.multiplier(CrashFlightMath.secondsTo(multiplier)), 1e-9)
        }
    }

    @Test fun instantCrashCannotBeCollectedOnTheFirstFrame() {
        val flight = CrashFlight(123L, 1.0)
        assertTrue(flight.hasCrashed(123L))
        assertNull(flight.cashoutMultiplier(123L))
        assertEquals(1.0, flight.visibleMultiplier(123L), 0.0)
    }

    @Test fun cashoutMustBeStrictlyBeforeTheMonotonicDeadline() {
        val flight = CrashFlight(12_000_000_000L, 2.0)
        val deadline = flight.startedAtNanos + flight.durationNanos
        assertNotNull(flight.cashoutMultiplier(deadline - 1L))
        assertNull(flight.cashoutMultiplier(deadline))
        assertNull(flight.cashoutMultiplier(deadline + 1L))
    }

    @Test fun skippedFramesNeverOvershootOrRestartTheExplosion() {
        val flight = CrashFlight(0L, 1.5)
        val late = flight.durationNanos + 8_000_000_000L
        assertEquals(1.5, flight.visibleMultiplier(late), 0.0)
        assertEquals(flight.durationSeconds, flight.visibleSeconds(late), 0.0)
        assertEquals(1f, flight.burstProgress(late), 0f)
    }

    @Test fun samplingCadenceCannotChangePositionOrResult() {
        val flight = CrashFlight(91_000_000_000L, 1000.0)
        val target = flight.startedAtNanos + 7_250_000_000L
        val expected = flight.visibleMultiplier(target)
        val position = CrashTrajectory.point(CrashFlightMath.travel(flight.visibleSeconds(target)))
        listOf(8_333_333L, 16_666_667L, 33_333_333L, 500_000_000L).forEach { frameStep ->
            var now = flight.startedAtNanos
            while (now < target) {
                flight.visibleMultiplier(now)
                now += frameStep
            }
            assertEquals(expected, flight.visibleMultiplier(target), 0.0)
            assertEquals(position, CrashTrajectory.point(CrashFlightMath.travel(flight.visibleSeconds(target))))
        }
    }

    @Test fun trajectoryNeverUsesTheHiddenCrashPointToSetSpeed() {
        val shortFlight = CrashFlight(0L, 2.0)
        val longFlight = CrashFlight(0L, 1000.0)
        val now = 1_000_000_000L
        assertEquals(
            CrashTrajectory.point(CrashFlightMath.travel(shortFlight.visibleSeconds(now))),
            CrashTrajectory.point(CrashFlightMath.travel(longFlight.visibleSeconds(now)))
        )
    }

    @Test fun bezierPrefixAndRocketNozzleShareTheExactSameCurve() {
        for (step in 0..100) {
            val progress = step / 100.0
            val start = CrashTrajectory.start
            val control = CrashTrajectory.prefixControl(progress)
            val end = CrashTrajectory.point(progress)
            for (sample in 0..10) {
                val u = sample / 10.0
                val q = 1.0 - u
                val x = q * q * start.x + 2.0 * q * u * control.x + u * u * end.x
                val y = q * q * start.y + 2.0 * q * u * control.y + u * u * end.y
                val expected = CrashTrajectory.point(progress * u)
                assertEquals(expected.x, x, 1e-12)
                assertEquals(expected.y, y, 1e-12)
            }
        }
    }

    @Test fun analyticHeadingMatchesTheTangentInPortraitAndLandscape() {
        listOf(360.0 to 480.0, 700.0 to 230.0).forEach { (width, height) ->
            for (step in 1..99) {
                val p = step / 100.0
                val before = CrashTrajectory.point(p - 1e-6)
                val after = CrashTrajectory.point(p + 1e-6)
                val numerical = atan2((after.y - before.y) * height, (after.x - before.x) * width)
                assertEquals(numerical, CrashTrajectory.headingRadians(p, width, height), 1e-8)
            }
        }
    }

    @Test fun travelHasNoHardCameraStops() {
        for (step in 1..300) {
            val t = step / 10.0
            val before = CrashFlightMath.travel(t - .001)
            val at = CrashFlightMath.travel(t)
            val after = CrashFlightMath.travel(t + .001)
            assertTrue(before < at && at < after)
            assertEquals(at - before, after - at, 1.1e-8)
        }
    }

    @Test fun maximumRoundStaysInsideTheCanvas() {
        val progress = CrashFlightMath.travel(CrashFlightMath.secondsTo(1000.0))
        val point = CrashTrajectory.point(progress)
        assertTrue(point.x < .85 && point.x > .7)
        assertTrue(point.y > .20 && point.y < .4)
        assertTrue(CrashTrajectory.headingRadians(progress, 360.0, 450.0).isFinite())
    }

    @Test fun processRecoveryLosesLaunchedUncollectedRoundOnlyOnce() {
        val original = WagerRecord("crash-1", "Crash", 1000L, 0L, "seed", "client")
        val armed = WagerRules.checkpoint(original, original.active(), 0.0, "Crash interrupted", "", false)
        val recovered = WagerRules.cancel(armed, armed.active(), 9000L, 1L)
        assertEquals(9000L, recovered.balance)
        assertEquals(0L, recovered.record.payout)
        val repeated = WagerRules.cancel(recovered.record, armed.active(), recovered.balance, 2L)
        assertFalse(repeated.changed)
        assertEquals(9000L, repeated.balance)
    }

    @Test fun confirmedCashoutReplacesFallbackAndSurvivesALaterCrash() {
        val original = WagerRecord("crash-2", "Crash", 1000L, 0L, "seed", "client")
        val armed = WagerRules.checkpoint(original, original.active(), 0.0, "Crash interrupted", "", false)
        val collected = WagerRules.checkpoint(armed, armed.active(), 2.0, "Collected", "", true)
        val settled = WagerRules.settle(collected, collected.active(), 9000L, 0.0, "Late crash", "", 10L)
        assertEquals(2000L, settled.record.payout)
        assertEquals(11000L, settled.balance)
    }
}
