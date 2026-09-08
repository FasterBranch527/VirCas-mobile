package com.vircas.mobile.game

import com.vircas.mobile.core.game.CrashBlast
import com.vircas.mobile.core.game.CrashFlight
import com.vircas.mobile.core.game.CrashFlightMath
import com.vircas.mobile.core.game.CrashTrajectory
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class CrashBlastTest {
    @Test fun blastStartsAtTheRocketFuelChamber() {
        val flight = CrashFlight(0L, 3.0)
        for ((w, h) in listOf(332.0 to 400.0, 516.0 to 230.0)) {
            val p = CrashFlightMath.travel(flight.durationSeconds)
            val nozzle = CrashTrajectory.point(p)
            val heading = CrashTrajectory.headingRadians(p, w, h)
            val center = CrashBlast.bodyCenter(flight, w, h, .92)
            assertEquals(nozzle.x * w + cos(heading) * 22.0 * .92, center.x, 1e-10)
            assertEquals(nozzle.y * h + sin(heading) * 22.0 * .92, center.y, 1e-10)
            assertEquals(22.0 * .92, hypot(center.x - nozzle.x * w, center.y - nozzle.y * h), 1e-10)
        }
    }

    @Test fun waveStartsLocallyAndExpandsMonotonically() {
        assertEquals(0.0, CrashBlast.waveProgress(0.0), 0.0)
        var previous = 0.0
        for (frame in 0..240) {
            val next = CrashBlast.waveProgress(frame / 120.0)
            assertTrue(next in 0.0..1.0 && next >= previous)
            previous = next
        }
        assertEquals(1.0, CrashBlast.waveProgress(CrashBlast.COVER_SECONDS), 0.0)
    }

    @Test fun opaqueWaveCoversEveryCornerBeforeReset() {
        for ((w, h) in listOf(320.0 to 800.0, 1000.0 to 360.0)) {
            for ((x, y) in listOf(0.0 to 0.0, w to h, w * .73 to h * .4, -120.0 to -260.0)) {
                val opaqueRadius = CrashBlast.coveringRadius(x, y, w, h) * CrashBlast.OPAQUE_RADIUS_FRACTION * CrashBlast.waveProgress(CrashBlast.COVER_SECONDS)
                for ((cx, cy) in listOf(0.0 to 0.0, w to 0.0, 0.0 to h, w to h)) {
                    assertTrue(opaqueRadius > hypot(cx - x, cy - y))
                }
            }
        }
    }

    @Test fun aSlowSaveKeepsTheScreenCoveredUntilRevealIsAcknowledged() {
        assertEquals(1f, CrashBlast.overlayAlpha(null, 900_000_000_000L), 0f)
        val reveal = 900_000_000_000L
        assertEquals(1f, CrashBlast.overlayAlpha(reveal, reveal), 0f)
        assertEquals(.5f, CrashBlast.overlayAlpha(reveal, reveal + 425_000_000L), 1e-6f)
        assertEquals(0f, CrashBlast.overlayAlpha(reveal, reveal + 850_000_000L), 0f)
    }

    @Test fun resumedFramesUseOriginalBlastAgeRatherThanRestarting() {
        val flight = CrashFlight(10_000_000_000L, 1.0)
        assertEquals(.5, CrashBlast.age(flight, 10_500_000_000L), 1e-9)
        assertEquals(7.0, CrashBlast.age(flight, 17_000_000_000L), 1e-9)
        assertEquals(1.0, CrashBlast.waveProgress(CrashBlast.age(flight, 17_000_000_000L)), 0.0)
    }

    @Test fun revealIsClampedAndIndependentOfFrameRate() {
        val start = 1_000_000_000L
        assertEquals(1f, CrashBlast.overlayAlpha(start, start - 1L), 0f)
        assertEquals(0f, CrashBlast.overlayAlpha(start, start + 10_000_000_000L), 0f)
        val sharedFrame = start + 350_000_000L
        val expected = CrashBlast.overlayAlpha(start, sharedFrame)
        listOf(8_333_333L, 16_666_667L, 33_333_333L).forEach { step ->
            var time = start
            while (time < sharedFrame) { CrashBlast.overlayAlpha(start, time); time += step }
            assertEquals(expected, CrashBlast.overlayAlpha(start, sharedFrame), 0f)
        }
    }
}
