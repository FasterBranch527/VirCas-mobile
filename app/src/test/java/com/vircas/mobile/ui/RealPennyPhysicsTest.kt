package com.vircas.mobile.ui

import com.vircas.mobile.game.engines.CoinflipEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealPennyPhysicsTest {

    private val referenceImpulse = RealPennyImpulse(
        startX = .04f,
        velocityX = .74f,
        velocityY = 4.72f,
        velocityZ = 4.28f,
        angularVelocityX = 1_880f,
        angularVelocityY = -143f,
        angularVelocityZ = 117f,
        restitution = .34f,
        floorFriction = .78f,
        wallRestitution = .42f
    )

    @Test
    fun resultSideNeverChangesTranslation() {
        val heads = simulateRealPennyMotion(referenceImpulse, CoinflipEngine.Side.HEADS)
        val tails = simulateRealPennyMotion(referenceImpulse, CoinflipEngine.Side.TAILS)

        assertEquals(heads.frames.size, tails.frames.size)
        heads.frames.zip(tails.frames).forEachIndexed { index, (a, b) ->
            assertEquals("x changed at frame $index", a.x, b.x, 0f)
            assertEquals("y changed at frame $index", a.y, b.y, 0f)
            assertEquals("z changed at frame $index", a.z, b.z, 0f)
        }
    }

    @Test
    fun differentImpulsesProduceDifferentRestingCoordinates() {
        val left = simulateRealPennyMotion(
            referenceImpulse.copy(velocityX = -1.18f, velocityZ = 2.05f, floorFriction = .68f),
            CoinflipEngine.Side.HEADS
        ).frames.last()

        val right = simulateRealPennyMotion(
            referenceImpulse.copy(velocityX = 1.21f, velocityZ = 4.62f, floorFriction = .90f),
            CoinflipEngine.Side.HEADS
        ).frames.last()

        val separation = kotlin.math.abs(left.x - right.x) + kotlin.math.abs(left.z - right.z)
        assertTrue("different impulses collapsed to effectively the same landing", separation > .20f)
    }

    @Test
    fun tossActuallyTravelsTowardTheCamera() {
        val motion = simulateRealPennyMotion(referenceImpulse, CoinflipEngine.Side.HEADS)
        val startZ = motion.frames.first().z
        val nearestZ = motion.frames.maxOf { it.z }

        assertTrue("coin never approached the camera", nearestZ - startZ > 1.75f)
    }

    @Test
    fun simulatedBodyNeverEscapesTheCollisionEnvelope() {
        val motion = simulateRealPennyMotion(referenceImpulse, CoinflipEngine.Side.TAILS)

        motion.frames.forEachIndexed { index, frame ->
            assertTrue("x escaped table at frame $index: ${frame.x}", frame.x in -1.45f..1.45f)
            // The launch begins slightly behind the table edge at z=-1.46, then immediately enters
            // the collision volume and is constrained to -1.25..1.15 for the rest of the toss.
            assertTrue("z escaped launch/table envelope at frame $index: ${frame.z}", frame.z in -1.46f..1.15f)
            assertTrue("coin fell through table at frame $index: ${frame.y}", frame.y >= PENNY_FLOOR_Y)
        }
    }
}
