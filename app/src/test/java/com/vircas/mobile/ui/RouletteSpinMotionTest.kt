package com.vircas.mobile.ui

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class RouletteSpinMotionTest {
    // Kotlin's remainder can be negative: normalize before comparing unwrapped revolutions.
    private fun delta(a: Double, b: Double) = ((((a - b) % 360.0) + 540.0) % 360.0) - 180.0

    @Test fun `all pockets land at their exact rendered center across consecutive spins`() {
        var start = RouletteMotionFrame()
        repeat(4) { run ->
            repeat(37) { index ->
                val motion = RouletteSpinMotion(start, index, variation = run * 37 + index)
                val frame = motion.settled()
                val expected = frame.wheelRotation - 90.0 + (index + .5) * 360.0 / 37.0
                assertEquals(0.0, delta(frame.ballRotation.toDouble(), expected), .002)
                assertEquals(.560f, frame.ballRadius, .00001f)
                assertEquals(0f, frame.ballHop, .00001f)
                assertEquals(1f, frame.ballDrop, .00001f)
                assertEquals(RouletteSpinPhase.SETTLED, frame.phase)
                assertEquals(0f, frame.wheelSpeed, 0f)
                assertEquals(0f, frame.ballSpeed, 0f)
                start = frame
            }
        }
    }

    @Test fun `wheel rests while the same ball curve keeps coasting`() {
        val motion = RouletteSpinMotion(RouletteMotionFrame(), 0)
        val stopped = motion.sample(RouletteSpinMotion.WHEEL_STOP_SECONDS)
        val later = motion.sample(4.25)
        assertEquals(stopped.wheelRotation, later.wheelRotation, 0f)
        assertEquals(0f, stopped.wheelSpeed, 0f)
        assertTrue(later.ballRotation < stopped.ballRotation - 180f)
        assertTrue(later.ballSpeed < 0f)
        assertEquals(RouletteSpinPhase.BALL_COAST, later.phase)
    }

    @Test fun `phase changes have continuous position radius and speed`() {
        val motion = RouletteSpinMotion(RouletteMotionFrame(ballRadius = .56f), 36)
        listOf(3.20, 4.80, 5.75, 6.35).forEach { boundary ->
            val before = motion.sample(boundary - .0001)
            val after = motion.sample(boundary + .0001)
            assertTrue(abs(after.ballRotation - before.ballRotation) < .20f)
            assertTrue(abs(after.ballRadius - before.ballRadius) < .001f)
            assertTrue(abs(after.ballSpeed - before.ballSpeed) < 1f)
            assertTrue(abs(after.wheelSpeed - before.wheelSpeed) < 1f)
        }
    }

    @Test fun `capture never runs another full revolution between pockets`() {
        repeat(37) { index ->
            val motion = RouletteSpinMotion(RouletteMotionFrame(), index)
            var previous = motion.sample(0.0)
            repeat(763) { step ->
                val next = motion.sample((step + 1) / 120.0)
                assertTrue(next.wheelRotation >= previous.wheelRotation)
                if (step / 120.0 < RouletteSpinMotion.CAPTURE_SECONDS - .01) {
                    assertTrue(next.ballRotation <= previous.ballRotation + .001f)
                }
                assertTrue(abs(next.ballRotation - previous.ballRotation) < 15f)
                assertTrue(next.ballRadius in .55f.. .94f)
                assertTrue(next.ballHop in 0f..1f)
                previous = next
            }
        }
    }

    @Test fun `sampling is seekable and independent of dropped frames`() {
        val motion = RouletteSpinMotion(RouletteMotionFrame(), 17, variation = Int.MAX_VALUE)
        val expected = motion.sample(4.93)
        listOf(.1, 3.0, .2, 6.35, 1.6).forEach { motion.sample(it) }
        assertEquals(expected, motion.sample(4.93))
        assertEquals(motion.sample(0.0), motion.sample(-5.0))
        assertEquals(motion.sample(6.35), motion.sample(600.0))
    }

    @Test fun `reduced motion final pose matches animated final pose without changing result`() {
        val motion = RouletteSpinMotion(RouletteMotionFrame(), 19)
        val animated = motion.sample(RouletteSpinMotion.DURATION_SECONDS)
        val skipped = motion.settled()
        assertEquals(0.0, delta(animated.ballRotation.toDouble(), skipped.ballRotation.toDouble()), .002)
        assertEquals(0.0, delta(animated.wheelRotation.toDouble(), skipped.wheelRotation.toDouble()), .002)
        assertEquals(animated.ballRadius, skipped.ballRadius, 0f)
    }
}
