package com.vircas.mobile.game.engines

import com.vircas.mobile.core.data.GameHistoryEntity
import com.vircas.mobile.core.progression.UserProgress
import com.vircas.mobile.core.random.SeededRandomProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RegressionSemanticsTest {
    @Test
    fun pushIsNeitherWinNorLoss() {
        val push = GameHistoryEntity(
            game = "Blackjack",
            timestamp = 1L,
            stake = 1_000L,
            payout = 1_000L,
            multiplier = 1.0,
            result = "PUSH"
        )

        assertFalse(push.won)
        assertFalse(push.lost)
        assertEquals(0L, push.profitLoss)

        val progressWithOneWinOneLossOnePush = UserProgress(
            gamesPlayed = 3,
            totalWins = 1,
            totalLosses = 1
        )
        assertEquals(50.0, progressWithOneWinOneLossOnePush.winRate, 0.0001)
    }

    @Test
    fun seededHorseRaceIsReproducible() {
        fun run(seed: Long): Pair<HorseRace, HorseRaceResult> {
            val engine = HorseRacingEngine(SeededRandomProvider(seed))
            val race = engine.generateRace(id = "regression", count = 8)
            return race to engine.simulate(race)
        }

        val first = run(42L)
        val second = run(42L)

        assertEquals(first.first, second.first)
        assertEquals(first.second, second.second)
    }
}
