package com.vircas.mobile.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// Presentation states only. The domain result is fixed before this timeline is created.
enum class RouletteSpinPhase { IDLE, WHEEL_AND_BALL, BALL_COAST, POCKET_BOUNCE, BALL_DROP, SETTLED }

internal data class RouletteMotionFrame(
    val wheelRotation: Float = 0f,
    val ballRotation: Float = -72f,
    val ballRadius: Float = .906f,
    val ballHop: Float = 0f,
    val ballDrop: Float = 0f,
    val wheelSpeed: Float = 0f,
    val ballSpeed: Float = 0f,
    val phase: RouletteSpinPhase = RouletteSpinPhase.IDLE
)

/**
 * A seekable, frame-rate independent presentation, not a physics-based outcome generator.
 * Integrated velocity curves start at rest and have zero terminal velocity. In particular,
 * stopping the wheel never restarts the ball's easing or chooses another equivalent angle.
 * The ball coasts for 2.55 seconds AFTER the rotor stops, then settles inside the fixed pocket.
 */
internal class RouletteSpinMotion(
    start: RouletteMotionFrame,
    winningIndex: Int,
    pocketCount: Int = 37,
    variation: Int = 0
) {
    init {
        require(pocketCount > 0 && winningIndex in 0 until pocketCount)
        require(start.wheelRotation.isFinite() && start.ballRotation.isFinite())
        require(start.ballRadius.isFinite())
    }

    private val wheelStart = normalize(start.wheelRotation.toDouble())
    private val ballStart = normalize(start.ballRotation.toDouble())
    private val initialRadius = start.ballRadius.toDouble().coerceIn(.48, .94)
    private val sweep = 360.0 / pocketCount
    private val wheelDistance = 360.0 * 4.0 + 110.0 + Math.floorMod(variation.toLong() * 37L, 140L)
    private val wheelEnd = wheelStart + wheelDistance
    private val pocketAngle = wheelEnd - 90.0 + (winningIndex + .5) * sweep
    private val ballDistance = 360.0 * 9.0 + normalize(ballStart - pocketAngle)
    private val ballEnd = ballStart - ballDistance
    private val bounceStartAngle = ballStart - ballDistance * ballTravel(COAST_END_SECONDS / CAPTURE_SECONDS)

    fun sample(seconds: Double): RouletteMotionFrame {
        require(seconds.isFinite())
        val t = seconds.coerceIn(0.0, DURATION_SECONDS)
        val wheelT = (t / WHEEL_STOP_SECONDS).coerceIn(0.0, 1.0)
        val ballT = (t / CAPTURE_SECONDS).coerceIn(0.0, 1.0)
        val capture = ((t - COAST_END_SECONDS) / (CAPTURE_SECONDS - COAST_END_SECONDS)).coerceIn(0.0, 1.0)
        val settle = ((t - CAPTURE_SECONDS) / (DURATION_SECONDS - CAPTURE_SECONDS)).coerceIn(0.0, 1.0)
        val angle = ballStart - ballDistance * ballTravel(ballT)
        val pocketCrossings = (bounceStartAngle - angle) / sweep
        val hopEnvelope = smooth(capture / .14) * (1.0 - capture) * (1.0 - capture)
        val pocketHop = abs(sin(pocketCrossings * PI)) * hopEnvelope * .85
        // Small damped movement inside ONE pocket; no extra turns, last-frame snap or reroll.
        val settleEnvelope = settle * settle * (1.0 - settle) * (1.0 - settle)
        val rock = sweep * .8 * sin(settle * PI * 4.0) * settleEnvelope
        val radial = when {
            t <= WHEEL_STOP_SECONDS -> mix(initialRadius, .906, smooth(t / .55))
            t <= COAST_END_SECONDS -> mix(.906, .790, smooth((t - WHEEL_STOP_SECONDS) / (COAST_END_SECONDS - WHEEL_STOP_SECONDS)))
            t <= CAPTURE_SECONDS -> mix(.790, .572, smooth(capture))
            else -> mix(.572, .560, smooth(settle))
        }
        return RouletteMotionFrame(
            wheelRotation = (wheelStart + wheelDistance * wheelTravel(wheelT)).toFloat(),
            ballRotation = (angle + rock).toFloat(),
            ballRadius = radial.toFloat(),
            ballHop = (pocketHop + abs(sin(settle * PI * 3.0)) * settleEnvelope * 2.0).toFloat(),
            ballDrop = smooth(settle).toFloat(),
            wheelSpeed = (wheelDistance / WHEEL_STOP_SECONDS * 20.0 * wheelT * cube(1.0 - wheelT)).toFloat(),
            ballSpeed = (-ballDistance / CAPTURE_SECONDS * 12.0 * ballT * square(1.0 - ballT)).toFloat(),
            phase = when {
                t >= DURATION_SECONDS -> RouletteSpinPhase.SETTLED
                t >= CAPTURE_SECONDS -> RouletteSpinPhase.BALL_DROP
                t >= COAST_END_SECONDS -> RouletteSpinPhase.POCKET_BOUNCE
                t >= WHEEL_STOP_SECONDS -> RouletteSpinPhase.BALL_COAST
                else -> RouletteSpinPhase.WHEEL_AND_BALL
            }
        )
    }

    fun settled(): RouletteMotionFrame = sample(DURATION_SECONDS).let {
        it.copy(wheelRotation = normalize(it.wheelRotation.toDouble()).toFloat(), ballRotation = normalize(ballEnd).toFloat())
    }

    companion object {
        const val WHEEL_STOP_SECONDS = 3.20
        const val COAST_END_SECONDS = 4.80
        const val CAPTURE_SECONDS = 5.75
        const val DURATION_SECONDS = 6.35
        private fun normalize(value: Double) = ((value % 360.0) + 360.0) % 360.0
        private fun square(value: Double) = value * value
        private fun cube(value: Double) = value * value * value
        private fun smooth(value: Double): Double {
            val p = value.coerceIn(0.0, 1.0)
            return p * p * (3.0 - 2.0 * p)
        }
        private fun mix(from: Double, to: Double, progress: Double) = from + (to - from) * progress
        private fun wheelTravel(p: Double) = 1.0 - square(square(1.0 - p)) * (1.0 + 4.0 * p)
        private fun ballTravel(p: Double) = p * p * (6.0 - 8.0 * p + 3.0 * p * p)
    }
}
