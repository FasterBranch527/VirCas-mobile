package com.vircas.mobile.ui

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

internal data class PlinkoPoint(val x: Float, val y: Float)

internal data class PlinkoPhysicsState(
    val position: PlinkoPoint,
    val velocity: PlinkoPoint,
    val nextRow: Int,
    val bucket: Int,
    val rotation: Float = 0f,
    val ageSeconds: Float = 0f,
    val bounceCount: Int = 0,
    val landed: Boolean = false
)

/**
 * Fixed-step, normalized-coordinate Plinko presentation physics.
 *
 * The wager engine chooses the left/right path before launch. This simulator turns that
 * committed path into gravity, peg impacts, restitution, drag, wall contacts and a gentle
 * post-board bucket correction. The correction keeps the animation faithful to the already
 * committed financial result without teleporting the ball between pegs.
 */
internal object PlinkoPhysics {
    const val Rows = 12
    const val BucketCount = Rows + 1
    const val BallRadius = 0.018f
    const val PegRadius = 0.0105f

    private const val Gravity = 2.15f
    private const val AirDrag = 0.28f
    private const val HorizontalSpacing = 0.068f
    private const val FirstPegY = 0.125f
    private const val RowSpacing = 0.059f
    private const val FloorY = 0.955f
    private const val LeftWall = 0.035f
    private const val RightWall = 0.965f
    private const val MaxFrameSeconds = 0.05f
    private const val FixedStepSeconds = 1f / 120f

    fun launch(path: List<Boolean>): PlinkoPhysicsState {
        require(path.size == Rows) { "Plinko path must contain $Rows decisions" }
        return PlinkoPhysicsState(
            position = PlinkoPoint(0.5f, 0.035f),
            velocity = PlinkoPoint(0f, 0.08f),
            nextRow = 0,
            bucket = path.count { it }
        )
    }

    fun peg(row: Int, path: List<Boolean>): PlinkoPoint {
        require(row in 0 until Rows)
        require(path.size == Rows)
        val rightsBefore = path.take(row).count { it }
        val x = 0.5f + (rightsBefore - row / 2f) * HorizontalSpacing
        return PlinkoPoint(x, FirstPegY + row * RowSpacing)
    }

    fun bucketCenter(bucket: Int): Float {
        require(bucket in 0 until BucketCount)
        return 0.5f + (bucket - Rows / 2f) * HorizontalSpacing
    }

    fun step(state: PlinkoPhysicsState, path: List<Boolean>, frameSeconds: Float): PlinkoPhysicsState {
        require(path.size == Rows)
        if (state.landed) return state

        var next = state
        var remaining = frameSeconds.coerceIn(0f, MaxFrameSeconds)
        while (remaining > 0f && !next.landed) {
            val slice = min(remaining, FixedStepSeconds)
            next = stepSlice(next, path, slice)
            remaining -= slice
        }
        return next
    }

    private fun stepSlice(state: PlinkoPhysicsState, path: List<Boolean>, dt: Float): PlinkoPhysicsState {
        var vx = state.velocity.x * exp(-AirDrag * dt)
        var vy = state.velocity.y + Gravity * dt
        var x = state.position.x + vx * dt
        var y = state.position.y + vy * dt
        var nextRow = state.nextRow
        var bounces = state.bounceCount

        if (x - BallRadius < LeftWall) {
            x = LeftWall + BallRadius
            vx = abs(vx) * 0.62f
        } else if (x + BallRadius > RightWall) {
            x = RightWall - BallRadius
            vx = -abs(vx) * 0.62f
        }

        if (nextRow < Rows) {
            val peg = peg(nextRow, path)
            if (vy > 0f && y + BallRadius >= peg.y - PegRadius) {
                val direction = if (path[nextRow]) 1f else -1f
                val contactRadius = BallRadius + PegRadius
                val contactX = direction * contactRadius * 0.72f
                val contactY = -sqrt(contactRadius * contactRadius - contactX * contactX)
                val incomingSpeed = sqrt(vx * vx + vy * vy)

                x = peg.x + contactX
                y = peg.y + contactY
                vx = vx * 0.26f + direction * (0.17f + min(0.14f, incomingSpeed * 0.08f))
                vy = -(0.105f + min(0.13f, abs(vy) * 0.24f))
                nextRow++
                bounces++
            }
        } else {
            // Once the last peg has committed the path, bucket rails progressively catch the ball.
            val target = bucketCenter(state.bucket)
            vx += (target - x) * 8.5f * dt
            vx *= exp(-1.4f * dt)
        }

        val landed = nextRow == Rows && y + BallRadius >= FloorY
        if (landed) {
            x = bucketCenter(state.bucket)
            y = FloorY - BallRadius
            vx = 0f
            vy = 0f
        }

        return state.copy(
            position = PlinkoPoint(x, y),
            velocity = PlinkoPoint(vx, vy),
            nextRow = nextRow,
            rotation = state.rotation + vx * dt / BallRadius,
            ageSeconds = state.ageSeconds + dt,
            bounceCount = bounces,
            landed = landed
        )
    }
}
