package com.vircas.mobile.game.engines

import com.vircas.mobile.core.random.RandomProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicEnginesTest {
    private object ZeroRandom : RandomProvider {
        override fun nextInt(from: Int, until: Int): Int = from
        override fun nextDouble(): Double = 0.0
    }

    @Test fun roulettePayoutsAndZeroAreCorrect() {
        val engine = RouletteEngine(ZeroRandom)
        assertEquals(36.0, engine.resolve(7, RouletteBet.Number(7)).payoutMultiplier, 0.0)
        assertTrue(engine.resolve(12, RouletteBet.Color(RouletteColor.RED)).won)
        assertFalse(engine.resolve(0, RouletteBet.Even).won)
        assertEquals(RouletteColor.GREEN, RouletteEngine.colorOf(0))
    }

    @Test fun blackjackScoresAcesCorrectly() {
        val soft17 = BlackjackEngine.score(listOf(PlayingCard(Rank.ACE, Suit.SPADES), PlayingCard(Rank.SIX, Suit.CLUBS)))
        assertEquals(17, soft17.total)
        assertTrue(soft17.soft)
        val hard17 = BlackjackEngine.score(listOf(PlayingCard(Rank.ACE, Suit.SPADES), PlayingCard(Rank.SIX, Suit.CLUBS), PlayingCard(Rank.TEN, Suit.HEARTS)))
        assertEquals(17, hard17.total)
        assertFalse(hard17.soft)
    }

    @Test fun deterministicPathGenerationCannotLoopForever() {
        val ladder = LadderEngine(ZeroRandom).newRound()
        assertTrue(ladder.safeByLevel.all { it.size == 3 })
        val towers = TowersEngine(ZeroRandom).newRound()
        assertTrue(towers.safeByLevel.all { it.size == 2 })
    }
}
