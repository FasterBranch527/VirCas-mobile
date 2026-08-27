package com.vircas.mobile.game.engines

import com.vircas.mobile.core.random.SeededRandomProvider
import org.junit.Assert.*
import org.junit.Test

class OriginalsEnginesTest {
    @Test fun diceAlwaysReturnsValidRange() {
        val engine = DiceEngine(SeededRandomProvider(42))
        repeat(100) { assertTrue(engine.roll(60.0, true).roll in 0.0..99.99) }
    }

    @Test fun minesCreatesRequestedUniqueMines() {
        val round = MinesEngine(SeededRandomProvider(7)).newRound(8)
        assertEquals(8, round.mineIndexes.size)
        assertTrue(round.mineIndexes.all { it in 0..24 })
    }

    @Test fun wheelUsesConfiguredSectors() {
        val engine = WheelEngine(SeededRandomProvider(1))
        repeat(50) { assertTrue(engine.spin().multiplier in engine.sectors) }
    }
}
