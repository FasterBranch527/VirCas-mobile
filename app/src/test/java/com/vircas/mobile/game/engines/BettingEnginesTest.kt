package com.vircas.mobile.game.engines

import com.vircas.mobile.core.random.RandomProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BettingEnginesTest {
    private object ZeroRandom : RandomProvider {
        override fun nextInt(from: Int, until: Int): Int = from
        override fun nextDouble(): Double = 0.0
    }

    @Test fun sportsEventsAreFictionalAndSettlementsWork() {
        val engine = SportsBettingEngine(ZeroRandom)
        val event = engine.generateEvents(now = 0L).first()
        val selections = engine.selections(event)
        val result = engine.simulate(event)
        val slip = BetSlipEngine().create(listOf(selections.first()), 1_000)
        assertEquals(event.id, result.eventId)
        assertTrue(slip.possiblePayout >= 1_000)
        assertTrue(BetSlipEngine().settle(slip, setOf(selections.first().id)) > 0)
    }

    @Test fun esportsMarketsResolveFromOnePrecomputedMatch() {
        val engine = EsportsBettingEngine(ZeroRandom)
        val event = engine.generate().first()
        val selections = engine.selections(event)
        val result = engine.simulate(event)

        assertEquals(8, selections.size)
        assertEquals(2, result.mapsA)
        assertEquals(0, result.mapsB)
        assertTrue("${event.id}:match:a" in result.winningSelectionIds)
        assertTrue("${event.id}:map1:a" in result.winningSelectionIds)
        assertTrue("${event.id}:maps:under" in result.winningSelectionIds)
        assertTrue("${event.id}:handicap:a" in result.winningSelectionIds)
    }

    @Test fun horseWinnerAndSharedSelectionsArePrecomputed() {
        val engine = HorseRacingEngine(ZeroRandom)
        val race = engine.generateRace(count = 6)
        val selections = engine.selections(race)
        val result = engine.simulate(race)

        assertEquals(6, result.finishOrder.distinct().size)
        assertEquals(6, selections.size)
        assertTrue(result.winnerId in race.horses.map { it.id })
        assertTrue(selections.any { it.id == "${race.id}:horse:${result.winnerId}" })
    }
}
