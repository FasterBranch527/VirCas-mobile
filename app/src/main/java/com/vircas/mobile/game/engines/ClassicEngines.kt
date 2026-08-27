package com.vircas.mobile.game.engines

import com.vircas.mobile.core.random.RandomProvider

enum class RouletteColor { RED, BLACK, GREEN }

sealed interface RouletteBet {
    data class Number(val number: Int) : RouletteBet
    data class Color(val color: RouletteColor) : RouletteBet
    data object Odd : RouletteBet
    data object Even : RouletteBet
    data object Low : RouletteBet
    data object High : RouletteBet
    data class Dozen(val index: Int) : RouletteBet
    data class Column(val index: Int) : RouletteBet
}

data class RouletteResult(
    val number: Int,
    val color: RouletteColor,
    val won: Boolean,
    val payoutMultiplier: Double
)

class RouletteEngine(private val random: RandomProvider) {
    fun spin(bet: RouletteBet): RouletteResult = resolve(random.nextInt(0, 37), bet)

    fun resolve(number: Int, bet: RouletteBet): RouletteResult {
        require(number in 0..36)
        val color = colorOf(number)
        val won = when (bet) {
            is RouletteBet.Number -> bet.number == number
            is RouletteBet.Color -> bet.color != RouletteColor.GREEN && bet.color == color
            RouletteBet.Odd -> number != 0 && number % 2 == 1
            RouletteBet.Even -> number != 0 && number % 2 == 0
            RouletteBet.Low -> number in 1..18
            RouletteBet.High -> number in 19..36
            is RouletteBet.Dozen -> {
                require(bet.index in 1..3)
                number in ((bet.index - 1) * 12 + 1)..(bet.index * 12)
            }
            is RouletteBet.Column -> {
                require(bet.index in 1..3)
                number != 0 && ((number - 1) % 3) + 1 == bet.index
            }
        }
        val multiplier = when (bet) {
            is RouletteBet.Number -> 36.0
            is RouletteBet.Dozen, is RouletteBet.Column -> 3.0
            else -> 2.0
        }
        return RouletteResult(number, color, won, if (won) multiplier else 0.0)
    }

    companion object {
        private val red = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)
        fun colorOf(number: Int): RouletteColor = when {
            number == 0 -> RouletteColor.GREEN
            number in red -> RouletteColor.RED
            else -> RouletteColor.BLACK
        }
    }
}

enum class Suit { CLUBS, DIAMONDS, HEARTS, SPADES }
enum class Rank(val pip: Int) {
    TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7), EIGHT(8), NINE(9), TEN(10), JACK(10), QUEEN(10), KING(10), ACE(11)
}
data class PlayingCard(val rank: Rank, val suit: Suit)
data class HandScore(val total: Int, val soft: Boolean)

enum class BlackjackStatus { PLAYER_TURN, DEALER_TURN, PLAYER_WIN, DEALER_WIN, PUSH, BLACKJACK }

data class BlackjackRound(
    val player: List<PlayingCard>,
    val dealer: List<PlayingCard>,
    val deck: List<PlayingCard>,
    val status: BlackjackStatus,
    val doubled: Boolean = false
) {
    val payoutMultiplier: Double
        get() = when (status) {
            BlackjackStatus.BLACKJACK -> 2.5
            BlackjackStatus.PLAYER_WIN -> 2.0
            BlackjackStatus.PUSH -> 1.0
            else -> 0.0
        }
}

class BlackjackEngine(private val random: RandomProvider) {
    fun newRound(): BlackjackRound {
        var deck = shuffledDeck()
        val player = listOf(deck[0], deck[2])
        val dealer = listOf(deck[1], deck[3])
        deck = deck.drop(4)
        val playerBj = isBlackjack(player)
        val dealerBj = isBlackjack(dealer)
        val status = when {
            playerBj && dealerBj -> BlackjackStatus.PUSH
            playerBj -> BlackjackStatus.BLACKJACK
            dealerBj -> BlackjackStatus.DEALER_WIN
            else -> BlackjackStatus.PLAYER_TURN
        }
        return BlackjackRound(player, dealer, deck, status)
    }

    fun hit(round: BlackjackRound): BlackjackRound {
        require(round.status == BlackjackStatus.PLAYER_TURN)
        val nextPlayer = round.player + round.deck.first()
        val next = round.copy(player = nextPlayer, deck = round.deck.drop(1))
        return if (score(nextPlayer).total > 21) next.copy(status = BlackjackStatus.DEALER_WIN) else next
    }

    fun double(round: BlackjackRound): BlackjackRound {
        require(round.status == BlackjackStatus.PLAYER_TURN && round.player.size == 2)
        val hit = hit(round).copy(doubled = true)
        return if (hit.status == BlackjackStatus.DEALER_WIN) hit else stand(hit)
    }

    fun stand(round: BlackjackRound): BlackjackRound {
        require(round.status == BlackjackStatus.PLAYER_TURN)
        var dealer = round.dealer
        var deck = round.deck
        while (score(dealer).total < 17) {
            dealer = dealer + deck.first()
            deck = deck.drop(1)
        }
        val playerScore = score(round.player).total
        val dealerScore = score(dealer).total
        val status = when {
            dealerScore > 21 -> BlackjackStatus.PLAYER_WIN
            dealerScore > playerScore -> BlackjackStatus.DEALER_WIN
            dealerScore < playerScore -> BlackjackStatus.PLAYER_WIN
            else -> BlackjackStatus.PUSH
        }
        return round.copy(dealer = dealer, deck = deck, status = status)
    }

    private fun shuffledDeck(): List<PlayingCard> {
        val cards = Suit.entries.flatMap { suit -> Rank.entries.map { rank -> PlayingCard(rank, suit) } }.toMutableList()
        for (i in cards.lastIndex downTo 1) {
            val j = random.nextInt(0, i + 1)
            val t = cards[i]
            cards[i] = cards[j]
            cards[j] = t
        }
        return cards
    }

    companion object {
        fun score(cards: List<PlayingCard>): HandScore {
            var total = cards.sumOf { it.rank.pip }
            var acesAsEleven = cards.count { it.rank == Rank.ACE }
            while (total > 21 && acesAsEleven > 0) {
                total -= 10
                acesAsEleven--
            }
            return HandScore(total, acesAsEleven > 0)
        }

        fun isBlackjack(cards: List<PlayingCard>): Boolean = cards.size == 2 && score(cards).total == 21
    }
}

enum class HiLoGuess { HIGHER, LOWER }
data class HiLoRound(val current: PlayingCard, val multiplier: Double = 1.0, val streak: Int = 0)
data class HiLoResult(val next: PlayingCard, val won: Boolean, val round: HiLoRound?, val payoutMultiplier: Double)

class HiLoEngine(private val random: RandomProvider) {
    fun newRound(): HiLoRound = HiLoRound(draw())

    fun guess(round: HiLoRound, guess: HiLoGuess): HiLoResult {
        val next = draw()
        val currentValue = rankValue(round.current.rank)
        val nextValue = rankValue(next.rank)
        val won = when (guess) {
            HiLoGuess.HIGHER -> nextValue > currentValue
            HiLoGuess.LOWER -> nextValue < currentValue
        }
        val winningRanks = when (guess) {
            HiLoGuess.HIGHER -> 14 - currentValue
            HiLoGuess.LOWER -> currentValue - 2
        }
        val chance = (winningRanks / 13.0).coerceAtLeast(1.0 / 13.0)
        val step = 0.98 / chance
        val multiplier = round.multiplier * step
        val nextRound = if (won) HiLoRound(next, multiplier, round.streak + 1) else null
        return HiLoResult(next, won, nextRound, if (won) multiplier else 0.0)
    }

    private fun draw(): PlayingCard = PlayingCard(
        Rank.entries[random.nextInt(0, Rank.entries.size)],
        Suit.entries[random.nextInt(0, Suit.entries.size)]
    )

    private fun rankValue(rank: Rank): Int = when (rank) {
        Rank.JACK -> 11
        Rank.QUEEN -> 12
        Rank.KING -> 13
        Rank.ACE -> 14
        else -> rank.pip
    }
}

data class PathRound(
    val safeByLevel: List<Set<Int>>,
    val level: Int = 0,
    val multiplier: Double = 1.0,
    val finished: Boolean = false
)
data class PathStep(val round: PathRound, val won: Boolean, val payoutMultiplier: Double)

class TowersEngine(private val random: RandomProvider) {
    private val floors = 8
    private val cells = 3

    fun newRound(): PathRound = PathRound(List(floors) { chooseUnique(cells, 2) })

    fun choose(round: PathRound, cell: Int): PathStep {
        require(!round.finished && round.level < floors && cell in 0 until cells)
        val safe = cell in round.safeByLevel[round.level]
        if (!safe) return PathStep(round.copy(finished = true), false, 0.0)
        val nextMultiplier = round.multiplier * (cells.toDouble() / 2.0) * 0.99
        val nextLevel = round.level + 1
        val next = round.copy(level = nextLevel, multiplier = nextMultiplier, finished = nextLevel == floors)
        return PathStep(next, true, nextMultiplier)
    }

    private fun chooseUnique(total: Int, count: Int): Set<Int> {
        require(count in 0..total)
        val pool = (0 until total).toMutableList()
        val out = linkedSetOf<Int>()
        repeat(count) { out += pool.removeAt(random.nextInt(0, pool.size)) }
        return out
    }
}

class LadderEngine(private val random: RandomProvider) {
    val multipliers = listOf(1.15, 1.35, 1.65, 2.1, 3.0, 5.0, 8.0, 15.0)
    private val tilesPerLevel = 4
    private val safePerLevel = 3

    fun newRound(): PathRound = PathRound(List(multipliers.size) { chooseUnique(tilesPerLevel, safePerLevel) })

    fun choose(round: PathRound, tile: Int): PathStep {
        require(!round.finished && round.level < multipliers.size && tile in 0 until tilesPerLevel)
        val safe = tile in round.safeByLevel[round.level]
        if (!safe) return PathStep(round.copy(finished = true), false, 0.0)
        val nextLevel = round.level + 1
        val value = multipliers[round.level]
        return PathStep(round.copy(level = nextLevel, multiplier = value, finished = nextLevel == multipliers.size), true, value)
    }

    private fun chooseUnique(total: Int, count: Int): Set<Int> {
        require(count in 0..total)
        val pool = (0 until total).toMutableList()
        val out = linkedSetOf<Int>()
        repeat(count) { out += pool.removeAt(random.nextInt(0, pool.size)) }
        return out
    }
}
