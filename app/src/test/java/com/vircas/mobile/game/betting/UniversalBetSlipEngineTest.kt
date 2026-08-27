package com.vircas.mobile.game.betting

import com.vircas.mobile.core.random.RandomProvider
import com.vircas.mobile.game.engines.Horse
import com.vircas.mobile.game.engines.HorseRace
import com.vircas.mobile.game.engines.MarketSelection
import com.vircas.mobile.game.engines.VirtualEvent
import com.vircas.mobile.game.engines.VirtualSport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalBetSlipEngineTest {
    private val fixedRandom = object : RandomProvider {
        override fun nextInt(from: Int, until: Int): Int = from
        override fun nextDouble(): Double = 0.0
    }

    @Test
    fun combinedOddsAndPossiblePayoutAreCalculatedForExpress() {
        val sport = sportPick(odds = 2.0)
        val horse = horsePick(odds = 2.5)

        val slip = UniversalBetSlip(listOf(sport, horse), stake = 1_000)

        assertEquals(5.0, slip.combinedOdds, 0.0001)
        assertEquals(5_000, slip.possiblePayout)
        assertTrue(slip.isExpress)
    }

    @Test(expected = IllegalArgumentException::class)
    fun slipRejectsTwoMarketsFromSameEvent() {
        val event = sportsEvent()
        UniversalBetSlip(
            selections = listOf(
                UniversalBetSelection.Sports(event, MarketSelection("sport_1:home", event.id, "Home", 2.0)),
                UniversalBetSelection.Sports(event, MarketSelection("sport_1:away", event.id, "Away", 2.0))
            ),
            stake = 500
        )
    }

    @Test
    fun mixedSportsAndHorseExpressSettlesFromOneRandomStream() {
        val selections = listOf(sportPick(2.0), horsePick(2.0))
        val engine = UniversalBetSlipEngine(fixedRandom)
        val slip = engine.create(selections, stake = 1_000)

        val settlement = engine.settle(slip)

        assertTrue(settlement.won)
        assertEquals(4.0, settlement.payoutMultiplier, 0.0001)
        assertEquals(4_000, settlement.payout)
        assertEquals(2, settlement.resultLines.size)
    }

    private fun sportPick(odds: Double): UniversalBetSelection.Sports {
        val event = sportsEvent()
        return UniversalBetSelection.Sports(
            event,
            MarketSelection("${event.id}:home", event.id, event.home, odds)
        )
    }

    private fun sportsEvent() = VirtualEvent(
        id = "sport_1",
        sport = VirtualSport.FOOTBALL,
        home = "Neon City",
        away = "Iron Vale",
        homeOdds = 2.0,
        drawOdds = null,
        awayOdds = 2.0,
        startsAt = 0L
    )

    private fun horsePick(odds: Double): UniversalBetSelection.HorseWin {
        val favorite = Horse("h1", "Solar Echo", odds, 100, 100, 100, 100)
        val outsider = Horse("h2", "Velvet Comet", 3.0, 60, 60, 60, 60)
        return UniversalBetSelection.HorseWin(HorseRace("race_test", listOf(favorite, outsider)), favorite)
    }
}
