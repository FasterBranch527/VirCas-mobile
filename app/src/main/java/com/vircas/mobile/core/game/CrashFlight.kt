package com.vircas.mobile.core.game

import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Presentation time only. CrashEngine still owns the distribution and payout rules. */
internal object CrashFlightMath {
    private const val LINEAR_GROWTH = 0.105
    private const val QUADRATIC_GROWTH = 0.0045

    // The original Crash growth curve, deliberately unchanged.
    fun multiplier(seconds: Double): Double {
        require(seconds.isFinite())
        val t = seconds.coerceAtLeast(0.0)
        return exp(LINEAR_GROWTH * t + QUADRATIC_GROWTH * t * t).coerceIn(1.0, 1000.0)
    }

    fun secondsTo(multiplier: Double): Double {
        require(multiplier.isFinite() && multiplier in 1.0..1000.0)
        val log = ln(multiplier)
        // Rationalized quadratic root remains accurate close to 1.00x.
        return 2.0 * log / (sqrt(LINEAR_GROWTH * LINEAR_GROWTH + 4.0 * QUADRATIC_GROWTH * log) + LINEAR_GROWTH)
    }

    /** C-infinity travel: no clamp thresholds, camera jumps or dependence on the hidden result. */
    fun travel(seconds: Double): Double {
        require(seconds.isFinite())
        return 1.0 - exp(-seconds.coerceAtLeast(0.0) / 10.0)
    }
}

/** All callers use elapsedRealtimeNanos, including taps, rendering and the background deadline. */
internal data class CrashFlight(val startedAtNanos: Long, val crashPoint: Double) {
    val durationSeconds = CrashFlightMath.secondsTo(crashPoint)
    val durationNanos = ceil(durationSeconds * 1_000_000_000.0).toLong()

    fun elapsedSeconds(nowNanos: Long): Double =
        (nowNanos - startedAtNanos).coerceAtLeast(0L) / 1_000_000_000.0

    fun hasCrashed(nowNanos: Long): Boolean =
        nowNanos - startedAtNanos >= durationNanos || CrashFlightMath.multiplier(elapsedSeconds(nowNanos)) >= crashPoint

    fun visibleSeconds(nowNanos: Long): Double = elapsedSeconds(nowNanos).coerceAtMost(durationSeconds)

    fun visibleMultiplier(nowNanos: Long): Double =
        if (hasCrashed(nowNanos)) crashPoint else CrashFlightMath.multiplier(elapsedSeconds(nowNanos))

    // Strictly before the crash, exactly as CrashEngine.cashOut. Never read a stale rendered frame.
    fun cashoutMultiplier(nowNanos: Long): Double? =
        if (hasCrashed(nowNanos)) null else CrashFlightMath.multiplier(elapsedSeconds(nowNanos))

    fun burstProgress(nowNanos: Long): Float =
        ((elapsedSeconds(nowNanos) - durationSeconds) / 0.85).coerceIn(0.0, 1.0).toFloat()
}

internal data class FlightPoint(val x: Double, val y: Double)

/** One quadratic Bezier for the trace, rocket nozzle and analytic heading. Coordinates are normalized. */
internal object CrashTrajectory {
    val start = FlightPoint(0.085, 0.83)
    val control = FlightPoint(0.48, 0.83)
    val end = FlightPoint(0.86, 0.23)

    fun point(progress: Double): FlightPoint {
        val p = progress.coerceIn(0.0, 1.0)
        val q = 1.0 - p
        return FlightPoint(
            q * q * start.x + 2.0 * q * p * control.x + p * p * end.x,
            q * q * start.y + 2.0 * q * p * control.y + p * p * end.y
        )
    }

    /** De Casteljau prefix: drawing this control point to point(p) ends exactly at the rocket. */
    fun prefixControl(progress: Double): FlightPoint {
        val p = progress.coerceIn(0.0, 1.0)
        return FlightPoint(start.x + (control.x - start.x) * p, start.y + (control.y - start.y) * p)
    }

    fun headingRadians(progress: Double, width: Double, height: Double): Double {
        require(width > 0.0 && height > 0.0)
        val p = progress.coerceIn(0.0, 1.0)
        val dx = 2.0 * ((1.0 - p) * (control.x - start.x) + p * (end.x - control.x)) * width
        val dy = 2.0 * ((1.0 - p) * (control.y - start.y) + p * (end.y - control.y)) * height
        return atan2(dy, dx)
    }
}
