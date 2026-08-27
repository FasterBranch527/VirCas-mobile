package com.vircas.mobile.game.engines

import com.vircas.mobile.core.random.RandomProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedEnginesTest {
    private object ZeroRandom : RandomProvider {
        override fun nextInt(from: Int, until: Int): Int = from
        override fun nextDouble(): Double = 0.0
    }

    @Test fun slotsResolvePaylinesFromPreselectedSymbols() {
        val spin = SlotsEngine(ZeroRandom).spin(SlotsEngine.NeonFruits)
        assertTrue(spin.grid.flatten().all { it.id == "CHERRY" })
        assertEquals(5, spin.lineWins.size)
        assertEquals(3.0, spin.payoutMultiplier, 0.0)
    }

    @Test fun crashResultIsKnownBeforeCashout() {
        val engine = CrashEngine(ZeroRandom)
        val round = CrashRound(2.0, 0.5)
        assertTrue(engine.cashOut(round, 1.5).won)
        assertFalse(engine.cashOut(round, 2.0).won)
    }

    @Test fun plinkoPathDeterminesBucket() {
        val result = PlinkoEngine(ZeroRandom).drop(PlinkoRisk.HIGH)
        assertEquals(0, result.bucket)
        assertEquals(45.0, result.multiplier, 0.0)
        assertEquals(12, result.path.size)
    }
}
