package com.vircas.mobile.game.engines

import com.vircas.mobile.core.random.RandomProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CasesEngineTest {
    private object ZeroRandom : RandomProvider {
        override fun nextInt(from: Int, until: Int): Int = from
        override fun nextDouble(): Double = 0.0
    }

    @Test fun caseOpeningPrecomputesWinnerAndReelStop() {
        val result = CasesEngine(ZeroRandom).open(CasesEngine.Starter)
        assertEquals(ItemRarity.COMMON, result.item.rarity)
        assertEquals(36, result.reel.size)
        assertEquals(result.item, result.reel[result.winningIndex])
        assertTrue(CasesEngine.All.all { it.cost > 0 && it.items.isNotEmpty() })
    }
}
