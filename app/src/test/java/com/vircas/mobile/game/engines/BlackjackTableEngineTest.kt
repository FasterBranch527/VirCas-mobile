package com.vircas.mobile.game.engines

import com.vircas.mobile.core.random.RandomProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlackjackTableEngineTest {
    private object ZeroRandom : RandomProvider {
        override fun nextInt(from: Int, until: Int): Int = from
        override fun nextDouble(): Double = 0.0
    }

    private val engine = BlackjackTableEngine(ZeroRandom)

    @Test
    fun splitCreatesTwoIndependentHandsAndConsumesTwoCards() {
        val round = BlackjackTableRound(
            hands = listOf(
                BlackjackTableHand(
                    listOf(PlayingCard(Rank.EIGHT, Suit.SPADES), PlayingCard(Rank.EIGHT, Suit.HEARTS))
                )
            ),
            dealer = listOf(PlayingCard(Rank.TEN, Suit.CLUBS), PlayingCard(Rank.SEVEN, Suit.DIAMONDS)),
            deck = listOf(
                PlayingCard(Rank.THREE, Suit.CLUBS),
                PlayingCard(Rank.FOUR, Suit.DIAMONDS),
                PlayingCard(Rank.FIVE, Suit.CLUBS)
            ),
            activeHandIndex = 0
        )

        assertTrue(engine.canSplit(round))
        val split = engine.split(round)

        assertEquals(2, split.hands.size)
        assertTrue(split.hands.all { it.fromSplit })
        assertTrue(split.hands.all { it.cards.size == 2 })
        assertEquals(1, split.deck.size)
        assertEquals(0, split.activeHandIndex)
    }

    @Test
    fun splitAcesReceiveOneCardEachAndAutoStand() {
        val round = BlackjackTableRound(
            hands = listOf(
                BlackjackTableHand(
                    listOf(PlayingCard(Rank.ACE, Suit.SPADES), PlayingCard(Rank.ACE, Suit.HEARTS))
                )
            ),
            dealer = listOf(PlayingCard(Rank.NINE, Suit.CLUBS), PlayingCard(Rank.SEVEN, Suit.DIAMONDS)),
            deck = listOf(PlayingCard(Rank.FIVE, Suit.CLUBS), PlayingCard(Rank.NINE, Suit.DIAMONDS)),
            activeHandIndex = 0
        )

        val split = engine.split(round)

        assertEquals(-1, split.activeHandIndex)
        assertTrue(split.hands.all { it.state == BlackjackHandState.STOOD })
        assertEquals(listOf(16, 20), split.hands.map { it.score.total })
    }

    @Test
    fun doubleAfterSplitUsesTwoBetUnitsAndExactlyOneCard() {
        val round = BlackjackTableRound(
            hands = listOf(
                BlackjackTableHand(
                    cards = listOf(PlayingCard(Rank.FIVE, Suit.SPADES), PlayingCard(Rank.SIX, Suit.HEARTS)),
                    fromSplit = true
                )
            ),
            dealer = listOf(PlayingCard(Rank.TEN, Suit.CLUBS), PlayingCard(Rank.SEVEN, Suit.DIAMONDS)),
            deck = listOf(PlayingCard(Rank.TEN, Suit.HEARTS), PlayingCard(Rank.TWO, Suit.CLUBS)),
            activeHandIndex = 0
        )

        assertTrue(engine.canDouble(round))
        val doubled = engine.double(round)

        assertEquals(2, doubled.hands.single().betUnits)
        assertEquals(3, doubled.hands.single().cards.size)
        assertEquals(21, doubled.hands.single().score.total)
        assertEquals(BlackjackHandState.STOOD, doubled.hands.single().state)
        assertEquals(-1, doubled.activeHandIndex)
    }

    @Test
    fun mixedSplitSettlementPaysOnlyWinningHand() {
        val round = BlackjackTableRound(
            hands = listOf(
                BlackjackTableHand(
                    cards = listOf(PlayingCard(Rank.TEN, Suit.SPADES), PlayingCard(Rank.NINE, Suit.HEARTS)),
                    fromSplit = true,
                    state = BlackjackHandState.WIN
                ),
                BlackjackTableHand(
                    cards = listOf(PlayingCard(Rank.TEN, Suit.DIAMONDS), PlayingCard(Rank.SIX, Suit.CLUBS)),
                    fromSplit = true,
                    state = BlackjackHandState.LOSS
                )
            ),
            dealer = listOf(PlayingCard(Rank.TEN, Suit.CLUBS), PlayingCard(Rank.EIGHT, Suit.DIAMONDS)),
            deck = emptyList(),
            activeHandIndex = -1,
            dealerPlayed = true
        )

        assertEquals(2, round.totalBetUnits)
        assertEquals(2.0, round.payoutUnits, 0.0)
        assertEquals(1.0, round.payoutMultiplier, 0.0)
    }

    @Test
    fun splitIsDisabledAtFourHands() {
        val pair = BlackjackTableHand(
            listOf(PlayingCard(Rank.EIGHT, Suit.SPADES), PlayingCard(Rank.EIGHT, Suit.HEARTS))
        )
        val round = BlackjackTableRound(
            hands = listOf(pair, pair.copy(state = BlackjackHandState.STOOD), pair.copy(state = BlackjackHandState.STOOD), pair.copy(state = BlackjackHandState.STOOD)),
            dealer = listOf(PlayingCard(Rank.TEN, Suit.CLUBS), PlayingCard(Rank.SEVEN, Suit.DIAMONDS)),
            deck = listOf(PlayingCard(Rank.THREE, Suit.CLUBS), PlayingCard(Rank.FOUR, Suit.DIAMONDS)),
            activeHandIndex = 0
        )

        assertFalse(engine.canSplit(round))
    }
}
