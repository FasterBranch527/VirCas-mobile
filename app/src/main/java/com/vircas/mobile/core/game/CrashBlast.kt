package com.vircas.mobile.core.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot
import kotlin.math.max

/** Presentation only: these timings must never change a wager, its RNG or its payout. */
internal object CrashBlast {
    const val COVER_SECONDS = 1.55
    const val REVEAL_SECONDS = 0.85
    const val RADIUS_OVERSCAN = 1.24
    const val OPAQUE_RADIUS_FRACTION = 0.84

    fun smooth(value: Double): Double {
        val t = value.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    fun age(flight: CrashFlight, nowNanos: Long): Double =
        (flight.elapsedSeconds(nowNanos) - flight.durationSeconds).coerceAtLeast(0.0)

    fun waveProgress(ageSeconds: Double): Double = smooth((ageSeconds - 0.06) / 1.40)

    fun overlayAlpha(revealStartedAtNanos: Long?, nowNanos: Long): Float =
        if (revealStartedAtNanos == null) 1f
        else (1.0 - smooth((nowNanos - revealStartedAtNanos).coerceAtLeast(0L) / 1_000_000_000.0 / REVEAL_SECONDS)).toFloat()

    /** The fuel chamber is 22 vector units ahead of the nozzle, never the screen center. */
    fun bodyCenter(flight: CrashFlight, width: Double, height: Double, rocketScale: Double): FlightPoint {
        require(rocketScale > 0.0 && rocketScale.isFinite())
        val travel = CrashFlightMath.travel(flight.durationSeconds)
        val nozzle = CrashTrajectory.point(travel)
        val heading = CrashTrajectory.headingRadians(travel, width, height)
        return FlightPoint(nozzle.x * width + cos(heading) * 22.0 * rocketScale, nozzle.y * height + sin(heading) * 22.0 * rocketScale)
    }

    /** Includes off-screen origins (e.g. a chart scrolled above the controls). */
    fun coveringRadius(originX: Double, originY: Double, width: Double, height: Double): Double {
        require(width > 0.0 && height > 0.0)
        require(originX.isFinite() && originY.isFinite() && width.isFinite() && height.isFinite())
        val dx = max(abs(originX), abs(width - originX))
        val dy = max(abs(originY), abs(height - originY))
        return hypot(dx, dy) * RADIUS_OVERSCAN
    }
}
