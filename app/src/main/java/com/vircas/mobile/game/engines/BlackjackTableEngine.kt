package com.vircas.mobile.game.engines

import com.vircas.mobile.core.random.RandomProvider

enum class BlackjackHandState {
    ACTIVE,
    STOOD,
    BUST,
    BLACKJACK,
    WIN,
    LOSS,
    PUSH
}

data class BlackjackTableHand(
    val cards: List<PlayingCard>,
    val betUnits: Int = 1,
    val fromSplit: Boolean = false,
    val state: BlackjackHandState = BlackjackHandState.ACTIVE
) {
    val score: HandScore get() = BlackjackEngine.score(cards)
    val isFinished: Boolean get() = state != BlackjackHandState.ACTIVE
}

data class BlackjackTableRound(
    val hands: List<BlackjackTableHand>,
    val dealer: List<PlayingCard>,
    val deck: List<PlayingCard>,
    val activeHandIndex: Int,
    val dealerPlayed: Boolean = false
) {
    val isPlayerTurn: Boolean get() = activeHandIndex in hands.indices
    val isComplete: Boolean get() = !isPlayerTurn && hands.all { it.state in terminalStates }
    val totalBetUnits: Int get() = hands.sumOf { it.betUnits }

    val payoutUnits: Double
        get() = hands.sumOf { hand ->
            hand.betUnits * when (hand.state) {
                BlackjackHandState.BLACKJACK -> 2.5
                BlackjackHandState.WIN -> 2.0
                BlackjackHandState.PUSH -> 1.0
                else -> 0.0
            }
        }

    val payoutMultiplier: Double
        get() = if (totalBetUnits == 0) 0.0 else payoutUnits / totalBetUnits.toDouble()

    companion object {
        private val terminalStates = setOf(
            BlackjackHandState.BUST,
            BlackjackHandState.BLACKJACK,
            BlackjackHandState.WIN,
            BlackjackHandState.LOSS,
            BlackjackHandState.PUSH
        )
    }
}

/**
 * Table-grade blackjack rules used by the animated casino screen.
 * - Single 52-card deck per round.
 * - Dealer stands on every 17, including soft 17.
 * - Natural blackjack pays 3:2.
 * - Up to four player hands via split.
 * - Split aces receive one card each and automatically stand.
 * - 21 after a split is a regular 1:1 win, not a natural blackjack.
 * - Double is available on any fresh two-card non-doubled hand, including split hands.
 */
class BlackjackTableEngine(private val random: RandomProvider) {
    fun newRound(): BlackjackTableRound {
        var deck = shuffledDeck()
        val player = listOf(deck[0], deck[2])
        val dealer = listOf(deck[1], deck[3])
        deck = deck.drop(4)

        val playerBlackjack = BlackjackEngine.isBlackjack(player)
        val dealerBlackjack = BlackjackEngine.isBlackjack(dealer)
        val hand = when {
            playerBlackjack && dealerBlackjack -> BlackjackTableHand(player, state = BlackjackHandState.PUSH)
            playerBlackjack -> BlackjackTableHand(player, state = BlackjackHandState.BLACKJACK)
            dealerBlackjack -> BlackjackTableHand(player, state = BlackjackHandState.LOSS)
            else -> BlackjackTableHand(player)
        }
        return BlackjackTableRound(
            hands = listOf(hand),
            dealer = dealer,
            deck = deck,
            activeHandIndex = if (hand.state == BlackjackHandState.ACTIVE) 0 else -1,
            dealerPlayed = dealerBlackjack || playerBlackjack
        )
    }

    fun canSplit(round: BlackjackTableRound): Boolean {
        if (!round.isPlayerTurn || round.hands.size >= MAX_HANDS) return false
        val hand = round.hands[round.activeHandIndex]
        return hand.state == BlackjackHandState.ACTIVE &&
            hand.cards.size == 2 &&
            hand.betUnits == 1 &&
            hand.cards[0].rank == hand.cards[1].rank
    }

    fun canDouble(round: BlackjackTableRound): Boolean {
        if (!round.isPlayerTurn) return false
        val hand = round.hands[round.activeHandIndex]
        return hand.state == BlackjackHandState.ACTIVE && hand.cards.size == 2 && hand.betUnits == 1
    }

    fun split(round: BlackjackTableRound): BlackjackTableRound {
        require(canSplit(round))
        require(round.deck.size >= 2)
        val index = round.activeHandIndex
        val original = round.hands[index]
        val splitAces = original.cards.first().rank == Rank.ACE

        val firstCards = listOf(original.cards[0], round.deck[0])
        val secondCards = listOf(original.cards[1], round.deck[1])
        val first = BlackjackTableHand(
            cards = firstCards,
            fromSplit = true,
            state = if (splitAces || BlackjackEngine.score(firstCards).total == 21) BlackjackHandState.STOOD else BlackjackHandState.ACTIVE
        )
        val second = BlackjackTableHand(
            cards = secondCards,
            fromSplit = true,
            state = if (splitAces || BlackjackEngine.score(secondCards).total == 21) BlackjackHandState.STOOD else BlackjackHandState.ACTIVE
        )
        val nextHands = round.hands.toMutableList().apply {
            removeAt(index)
            add(index, second)
            add(index, first)
        }
        val provisional = round.copy(hands = nextHands, deck = round.deck.drop(2), activeHandIndex = index)
        return provisional.copy(activeHandIndex = nextActiveIndex(provisional.hands, index))
    }

    fun hit(round: BlackjackTableRound): BlackjackTableRound {
        require(round.isPlayerTurn && round.deck.isNotEmpty())
        val index = round.activeHandIndex
        val hand = round.hands[index]
        val cards = hand.cards + round.deck.first()
        val total = BlackjackEngine.score(cards).total
        val state = when {
            total > 21 -> BlackjackHandState.BUST
            total == 21 -> BlackjackHandState.STOOD
            else -> BlackjackHandState.ACTIVE
        }
        val hands = round.hands.toMutableList().apply { this[index] = hand.copy(cards = cards, state = state) }
        val nextIndex = if (state == BlackjackHandState.ACTIVE) index else nextActiveIndex(hands, index + 1)
        return round.copy(hands = hands, deck = round.deck.drop(1), activeHandIndex = nextIndex)
    }

    fun stand(round: BlackjackTableRound): BlackjackTableRound {
        require(round.isPlayerTurn)
        val index = round.activeHandIndex
        val hands = round.hands.toMutableList().apply {
            this[index] = this[index].copy(state = BlackjackHandState.STOOD)
        }
        return round.copy(hands = hands, activeHandIndex = nextActiveIndex(hands, index + 1))
    }

    fun double(round: BlackjackTableRound): BlackjackTableRound {
        require(canDouble(round) && round.deck.isNotEmpty())
        val index = round.activeHandIndex
        val hand = round.hands[index]
        val cards = hand.cards + round.deck.first()
        val state = if (BlackjackEngine.score(cards).total > 21) BlackjackHandState.BUST else BlackjackHandState.STOOD
        val hands = round.hands.toMutableList().apply {
            this[index] = hand.copy(cards = cards, betUnits = 2, state = state)
        }
        return round.copy(
            hands = hands,
            deck = round.deck.drop(1),
            activeHandIndex = nextActiveIndex(hands, index + 1)
        )
    }

    fun playDealer(round: BlackjackTableRound): BlackjackTableRound {
        require(!round.isPlayerTurn)
        if (round.isComplete) return round

        var dealer = round.dealer
        var deck = round.deck
        val hasLiveHand = round.hands.any { it.state == BlackjackHandState.STOOD }
        if (hasLiveHand) {
            while (BlackjackEngine.score(dealer).total < 17) {
                require(deck.isNotEmpty())
                dealer = dealer + deck.first()
                deck = deck.drop(1)
            }
        }

        val dealerScore = BlackjackEngine.score(dealer).total
        val resolved = round.hands.map { hand ->
            if (hand.state != BlackjackHandState.STOOD) hand
            else {
                val state = when {
                    dealerScore > 21 -> BlackjackHandState.WIN
                    hand.score.total > dealerScore -> BlackjackHandState.WIN
                    hand.score.total < dealerScore -> BlackjackHandState.LOSS
                    else -> BlackjackHandState.PUSH
                }
                hand.copy(state = state)
            }
        }
        return round.copy(hands = resolved, dealer = dealer, deck = deck, activeHandIndex = -1, dealerPlayed = true)
    }

    private fun nextActiveIndex(hands: List<BlackjackTableHand>, start: Int): Int {
        for (index in start.coerceAtLeast(0) until hands.size) {
            if (hands[index].state == BlackjackHandState.ACTIVE) return index
        }
        return -1
    }

    private fun shuffledDeck(): List<PlayingCard> {
        val cards = Suit.entries.flatMap { suit -> Rank.entries.map { rank -> PlayingCard(rank, suit) } }.toMutableList()
        for (i in cards.lastIndex downTo 1) {
            val j = random.nextInt(0, i + 1)
            val tmp = cards[i]
            cards[i] = cards[j]
            cards[j] = tmp
        }
        return cards
    }

    companion object {
        const val MAX_HANDS = 4
    }
}
