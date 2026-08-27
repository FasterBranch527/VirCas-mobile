package com.vircas.mobile.game.engines

import com.vircas.mobile.core.random.RandomProvider
import kotlin.math.floor

sealed interface GameOutcome {
    val multiplier: Double
    data class Win(override val multiplier: Double, val label: String) : GameOutcome
    data class Loss(val label: String) : GameOutcome { override val multiplier = 0.0 }
}

class DiceEngine(private val random: RandomProvider) {
    data class Result(val roll: Double, val outcome: GameOutcome)

    fun roll(threshold: Double, under: Boolean): Result {
        require(threshold in 1.0..99.0)
        val value = floor(random.nextDouble() * 10_000.0) / 100.0
        val chance = if (under) threshold / 100.0 else (100.0 - threshold) / 100.0
        val won = if (under) value < threshold else value > threshold
        val payout = (0.99 / chance).coerceAtLeast(1.01)
        return Result(value, if (won) GameOutcome.Win(payout, "${"%.2f".format(value)}") else GameOutcome.Loss("${"%.2f".format(value)}"))
    }
}

class CoinflipEngine(private val random: RandomProvider) {
    enum class Side { HEADS, TAILS }
    data class Result(val side: Side, val outcome: GameOutcome)
    fun flip(pick: Side): Result {
        val side = if (random.nextInt(0, 2) == 0) Side.HEADS else Side.TAILS
        return Result(side, if (side == pick) GameOutcome.Win(1.98, side.name) else GameOutcome.Loss(side.name))
    }
}

class MinesEngine(private val random: RandomProvider) {
    data class Round(val mineIndexes: Set<Int>, val opened: Set<Int> = emptySet(), val mineCount: Int)

    fun newRound(mineCount: Int): Round {
        require(mineCount in 1..10)
        val mines = mutableSetOf<Int>()
        while (mines.size < mineCount) mines += random.nextInt(0, 25)
        return Round(mines, mineCount = mineCount)
    }

    fun reveal(round: Round, index: Int): Pair<Round, GameOutcome?> {
        require(index in 0..24)
        if (index in round.mineIndexes) return round to GameOutcome.Loss("Mine")
        val next = round.copy(opened = round.opened + index)
        val safeTiles = 25 - round.mineCount
        val multiplier = (0 until next.opened.size).fold(1.0) { acc, step ->
            acc * (25.0 - step) / (safeTiles.toDouble() - step)
        } * 0.99
        return next to GameOutcome.Win(multiplier, "Safe")
    }
}

class WheelEngine(private val random: RandomProvider) {
    val sectors = listOf(0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 5.0, 10.0, 25.0)
    fun spin(): GameOutcome {
        val value = sectors[random.nextInt(0, sectors.size)]
        return if (value > 0.0) GameOutcome.Win(value, "${value}x") else GameOutcome.Loss("0x")
    }
}
