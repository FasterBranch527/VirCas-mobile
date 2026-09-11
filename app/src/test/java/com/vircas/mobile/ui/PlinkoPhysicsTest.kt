package com.vircas.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlinkoPhysicsTest {
    @Test
    fun allLeftPathLandsInLeftEdgeBucket() {
        val path = List(PlinkoPhysics.Rows) { false }
        val landed = simulate(path)

        assertTrue(landed.landed)
        assertEquals(0, landed.bucket)
        assertEquals(PlinkoPhysics.Rows, landed.bounceCount)
        assertEquals(PlinkoPhysics.bucketCenter(0), landed.position.x, 0.0001f)
    }

    @Test
    fun allRightPathLandsInRightEdgeBucket() {
        val path = List(PlinkoPhysics.Rows) { true }
        val landed = simulate(path)

        assertTrue(landed.landed)
        assertEquals(PlinkoPhysics.Rows, landed.bucket)
        assertEquals(PlinkoPhysics.Rows, landed.bounceCount)
        assertEquals(PlinkoPhysics.bucketCenter(PlinkoPhysics.Rows), landed.position.x, 0.0001f)
    }

    @Test
    fun mixedPathKeepsCommittedBucket() {
        val path = List(PlinkoPhysics.Rows) { index -> index % 2 == 0 }
        val landed = simulate(path)

        assertEquals(path.count { it }, landed.bucket)
        assertEquals(PlinkoPhysics.Rows, landed.bounceCount)
        assertTrue(landed.ageSeconds in 1.5f..8f)
    }

    @Test
    fun unevenFramesRemainStable() {
        val path = listOf(true, false, true, true, false, false, true, false, true, false, true, false)
        var state = PlinkoPhysics.launch(path)
        repeat(300) { frame ->
            state = PlinkoPhysics.step(state, path, if (frame % 3 == 0) 0.09f else 0.011f)
            if (state.landed) return@repeat
        }

        assertTrue(state.landed)
        assertEquals(path.count { it }, state.bucket)
        assertEquals(PlinkoPhysics.Rows, state.bounceCount)
    }

    @Test
    fun landedBallIsAtRest() {
        val path = List(PlinkoPhysics.Rows) { it > 5 }
        val landed = simulate(path)
        val after = PlinkoPhysics.step(landed, path, 0.05f)

        assertEquals(landed, after)
        assertEquals(0f, after.velocity.x, 0f)
        assertEquals(0f, after.velocity.y, 0f)
        assertFalse(after.position.y > 1f)
    }

    private fun simulate(path: List<Boolean>): PlinkoPhysicsState {
        var state = PlinkoPhysics.launch(path)
        repeat(1_000) {
            if (!state.landed) state = PlinkoPhysics.step(state, path, 1f / 60f)
        }
        return state
    }
}
