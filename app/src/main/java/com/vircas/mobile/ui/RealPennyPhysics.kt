package com.vircas.mobile.ui

import com.vircas.mobile.game.engines.CoinflipEngine
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/**
 * Visual-only rigid-body approximation for the imported Lincoln penny.
 *
 * There is deliberately no sampled landing position in this model. A toss samples only its
 * initial linear/angular impulse plus material coefficients. The resting x/z coordinate emerges
 * from fixed-step integration, gravity, table impacts, wall impacts and friction.
 *
 * HEADS/TAILS is already fixed by CoinflipEngine before this visual motion is generated. The game
 * result is used only to gently settle the final face orientation; it never changes translation.
 */
internal data class RealPennyMotionFrame(
    val x: Float,
    val y: Float,
    val z: Float,
    val rotX: Float,
    val rotY: Float,
    val rotZ: Float,
    val grounded: Boolean
)

internal data class RealPennyMotion(
    val frames: List<RealPennyMotionFrame>,
    val durationMillis: Int = PENNY_MOTION_DURATION_MS
) {
    fun sample(progress: Float): RealPennyMotionFrame {
        if (frames.isEmpty()) return IDLE_FRAME
        if (frames.size == 1) return frames.first()

        val scaled = progress.coerceIn(0f, 1f) * frames.lastIndex
        val lo = floor(scaled).toInt().coerceIn(0, frames.lastIndex)
        val hi = (lo + 1).coerceAtMost(frames.lastIndex)
        val t = scaled - lo
        val a = frames[lo]
        val b = frames[hi]
        return RealPennyMotionFrame(
            x = lerpMotion(a.x, b.x, t),
            y = lerpMotion(a.y, b.y, t),
            z = lerpMotion(a.z, b.z, t),
            rotX = lerpMotion(a.rotX, b.rotX, t),
            rotY = lerpMotion(a.rotY, b.rotY, t),
            rotZ = lerpMotion(a.rotZ, b.rotZ, t),
            grounded = if (t < .5f) a.grounded else b.grounded
        )
    }

    companion object {
        val Idle = RealPennyMotion(listOf(IDLE_FRAME))
    }
}

internal data class RealPennyImpulse(
    val startX: Float,
    val velocityX: Float,
    val velocityY: Float,
    val velocityZ: Float,
    val angularVelocityX: Float,
    val angularVelocityY: Float,
    val angularVelocityZ: Float,
    val restitution: Float,
    val floorFriction: Float,
    val wallRestitution: Float
)

private val pennyPhysicsRandom = SecureRandom()

internal fun randomRealPennyMotion(resultSide: CoinflipEngine.Side): RealPennyMotion {
    val impulse = RealPennyImpulse(
        startX = randomRange(-.13f, .13f),
        velocityX = randomRange(-1.32f, 1.32f),
        velocityY = randomRange(4.05f, 5.20f),
        velocityZ = randomRange(1.78f, 4.55f),
        angularVelocityX = randomSignedRange(1_500f, 2_250f),
        angularVelocityY = randomRange(-330f, 330f),
        angularVelocityZ = randomRange(-260f, 260f),
        restitution = randomRange(.24f, .46f),
        floorFriction = randomRange(.66f, .91f),
        wallRestitution = randomRange(.25f, .60f)
    )
    return simulateRealPennyMotion(impulse, resultSide)
}

internal fun simulateRealPennyMotion(
    impulse: RealPennyImpulse,
    resultSide: CoinflipEngine.Side
): RealPennyMotion {
    val raw = ArrayList<RealPennyMotionFrame>(PENNY_FRAME_COUNT)

    var x = impulse.startX.coerceIn(-.20f, .20f)
    var y = PENNY_START_Y
    var z = PENNY_START_Z
    var vx = impulse.velocityX.coerceIn(-1.6f, 1.6f)
    var vy = impulse.velocityY.coerceIn(3.4f, 5.8f)
    var vz = impulse.velocityZ.coerceIn(1.4f, 4.9f)

    var rx = 90f + randomRange(-8f, 8f)
    var ry = randomRange(-8f, 8f)
    var rz = randomRange(0f, 360f)
    var wx = impulse.angularVelocityX.coerceIn(-2_500f, 2_500f)
    var wy = impulse.angularVelocityY.coerceIn(-430f, 430f)
    var wz = impulse.angularVelocityZ.coerceIn(-360f, 360f)

    val restitution = impulse.restitution.coerceIn(.18f, .52f)
    val floorFriction = impulse.floorFriction.coerceIn(.58f, .94f)
    val wallRestitution = impulse.wallRestitution.coerceIn(.20f, .66f)

    repeat(PENNY_FRAME_COUNT) {
        val grounded = y <= PENNY_FLOOR_Y + .002f && abs(vy) < .03f
        raw += RealPennyMotionFrame(x, y, z, rx, ry, rz, grounded)

        vy -= PENNY_GRAVITY * PENNY_DT
        x += vx * PENNY_DT
        y += vy * PENNY_DT
        z += vz * PENNY_DT

        rx += wx * PENNY_DT
        ry += wy * PENNY_DT
        rz += wz * PENNY_DT

        vx *= .9991f
        vz *= .9991f
        wx *= .9990f
        wy *= .9988f
        wz *= .9988f

        if (x < PENNY_MIN_X) {
            x = PENNY_MIN_X
            vx = abs(vx) * wallRestitution
            wz *= .72f
        } else if (x > PENNY_MAX_X) {
            x = PENNY_MAX_X
            vx = -abs(vx) * wallRestitution
            wz *= .72f
        }

        if (z < PENNY_MIN_Z) {
            z = PENNY_MIN_Z
            vz = abs(vz) * wallRestitution
            wy *= .74f
        } else if (z > PENNY_MAX_Z) {
            z = PENNY_MAX_Z
            vz = -abs(vz) * wallRestitution
            wy *= .74f
        }

        if (y < PENNY_FLOOR_Y) {
            y = PENNY_FLOOR_Y
            if (abs(vy) > .36f) {
                vy = -vy * restitution
                vx *= floorFriction
                vz *= floorFriction
                wx *= .62f
                wy *= .79f
                wz *= .79f
            } else {
                vy = 0f
                vx *= .915f
                vz *= .915f
                wx *= .89f
                wy *= .90f
                wz *= .90f
                if (abs(vx) < .010f) vx = 0f
                if (abs(vz) < .010f) vz = 0f
            }
        }
    }

    // Only the final orientation knows the fair game result. The entire x/y/z trajectory above
    // is untouched, so changing HEADS to TAILS cannot move the landing location.
    val settleStart = (raw.lastIndex * .77f).toInt()
    val rawFinalX = raw.last().rotX
    val faceBase = 90f + if (resultSide == CoinflipEngine.Side.HEADS) 0f else 180f
    val targetTurn = round((rawFinalX - faceBase) / 360f)
    val targetX = faceBase + targetTurn * 360f
    val finalYaw = raw.last().rotZ

    val frames = raw.mapIndexed { index, frame ->
        if (index < settleStart) {
            frame
        } else {
            val u = (index - settleStart).toFloat() / (raw.lastIndex - settleStart).coerceAtLeast(1)
            val s = smoothStepMotion(u)
            frame.copy(
                rotX = lerpMotion(frame.rotX, targetX, s),
                rotY = lerpMotion(frame.rotY, 0f, s),
                rotZ = lerpMotion(frame.rotZ, finalYaw, s)
            )
        }
    }

    return RealPennyMotion(frames)
}

private fun randomRange(min: Float, max: Float): Float =
    min + pennyPhysicsRandom.nextFloat() * (max - min)

private fun randomSignedRange(minMagnitude: Float, maxMagnitude: Float): Float {
    val magnitude = randomRange(minMagnitude, maxMagnitude)
    return if (pennyPhysicsRandom.nextBoolean()) magnitude else -magnitude
}

private fun lerpMotion(a: Float, b: Float, t: Float): Float = a + (b - a) * t
private fun smoothStepMotion(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private val IDLE_FRAME = RealPennyMotionFrame(
    x = 0f,
    y = -.30f,
    z = -.52f,
    rotX = 108f,
    rotY = -7f,
    rotZ = -9f,
    grounded = false
)

internal const val PENNY_FLOOR_Y = -.91f
private const val PENNY_START_Y = -.22f
private const val PENNY_START_Z = -1.46f
private const val PENNY_MIN_X = -1.45f
private const val PENNY_MAX_X = 1.45f
private const val PENNY_MIN_Z = -1.25f
private const val PENNY_MAX_Z = 1.15f
private const val PENNY_GRAVITY = 9.8f
private const val PENNY_HZ = 120
private const val PENNY_MOTION_DURATION_MS = 2550
private const val PENNY_FRAME_COUNT = 307
private const val PENNY_DT = 1f / PENNY_HZ
