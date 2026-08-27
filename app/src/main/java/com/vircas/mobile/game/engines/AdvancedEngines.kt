package com.vircas.mobile.game.engines

import com.vircas.mobile.core.random.RandomProvider
import kotlin.math.floor

data class SlotSymbol(val id: String, val weight: Int, val payout3: Double, val bonus: Boolean = false)
data class SlotTheme(val id: String, val title: String, val symbols: List<SlotSymbol>)
data class SlotLineWin(val lineIndex: Int, val symbolId: String, val multiplier: Double)
data class SlotSpinResult(
    val grid: List<List<SlotSymbol>>,
    val lineWins: List<SlotLineWin>,
    val bonusCount: Int,
    val payoutMultiplier: Double
)

class SlotsEngine(private val random: RandomProvider) {
    val paylines: List<List<Pair<Int, Int>>> = listOf(
        listOf(0 to 0, 0 to 1, 0 to 2),
        listOf(1 to 0, 1 to 1, 1 to 2),
        listOf(2 to 0, 2 to 1, 2 to 2),
        listOf(0 to 0, 1 to 1, 2 to 2),
        listOf(2 to 0, 1 to 1, 0 to 2)
    )

    fun spin(theme: SlotTheme): SlotSpinResult {
        require(theme.symbols.isNotEmpty() && theme.symbols.all { it.weight > 0 })
        val grid = List(3) { List(3) { pick(theme.symbols) } }
        val wins = paylines.mapIndexedNotNull { index, line ->
            val symbols = line.map { (row, column) -> grid[row][column] }
            val first = symbols.first()
            if (!first.bonus && symbols.all { it.id == first.id }) SlotLineWin(index, first.id, first.payout3) else null
        }
        val bonusCount = grid.flatten().count { it.bonus }
        val lineValue = wins.sumOf { it.multiplier } / paylines.size.toDouble()
        val bonusValue = when {
            bonusCount >= 5 -> 15.0
            bonusCount == 4 -> 8.0
            bonusCount == 3 -> 3.0
            else -> 0.0
        }
        return SlotSpinResult(grid, wins, bonusCount, lineValue + bonusValue)
    }

    private fun pick(symbols: List<SlotSymbol>): SlotSymbol {
        val total = symbols.sumOf { it.weight }
        var ticket = random.nextInt(0, total)
        for (symbol in symbols) {
            if (ticket < symbol.weight) return symbol
            ticket -= symbol.weight
        }
        return symbols.last()
    }

    companion object {
        val NeonFruits = SlotTheme("neon_fruits", "Neon Fruits", listOf(
            SlotSymbol("CHERRY", 30, 3.0), SlotSymbol("LEMON", 26, 4.0), SlotSymbol("GRAPE", 20, 6.0),
            SlotSymbol("SEVEN", 10, 12.0), SlotSymbol("NOVA", 6, 25.0), SlotSymbol("BONUS", 8, 0.0, true)
        ))
        val AncientGold = SlotTheme("ancient_gold", "Ancient Gold", listOf(
            SlotSymbol("SCARAB", 30, 3.0), SlotSymbol("ANKH", 25, 4.5), SlotSymbol("COBRA", 19, 7.0),
            SlotSymbol("PHARAOH", 10, 14.0), SlotSymbol("SUN", 7, 28.0), SlotSymbol("BONUS", 9, 0.0, true)
        ))
        val CyberVault = SlotTheme("cyber_vault", "Cyber Vault", listOf(
            SlotSymbol("CHIP", 31, 3.0), SlotSymbol("CORE", 24, 5.0), SlotSymbol("DRONE", 18, 8.0),
            SlotSymbol("VAULT", 10, 15.0), SlotSymbol("GLITCH", 7, 30.0), SlotSymbol("BONUS", 10, 0.0, true)
        ))
        val Themes = listOf(NeonFruits, AncientGold, CyberVault)
    }
}

data class CrashRound(val crashPoint: Double, val seedValue: Double)
data class CrashCashOut(val won: Boolean, val payoutMultiplier: Double)

class CrashEngine(private val random: RandomProvider) {
    fun newRound(): CrashRound {
        val u = random.nextDouble().coerceIn(0.0, 0.999999)
        val raw = if (u < 0.01) 1.0 else 0.99 / (1.0 - u)
        val point = (floor(raw.coerceIn(1.0, 1000.0) * 100.0) / 100.0).coerceAtLeast(1.0)
        return CrashRound(point, u)
    }

    fun cashOut(round: CrashRound, multiplier: Double): CrashCashOut {
        require(multiplier >= 1.0)
        val won = multiplier < round.crashPoint
        return CrashCashOut(won, if (won) multiplier else 0.0)
    }
}

enum class PlinkoRisk { LOW, MEDIUM, HIGH }
data class PlinkoResult(val path: List<Boolean>, val bucket: Int, val multiplier: Double)

class PlinkoEngine(private val random: RandomProvider, val rows: Int = 12) {
    init { require(rows == 12) }

    fun drop(risk: PlinkoRisk): PlinkoResult {
        val path = List(rows) { random.nextDouble() >= 0.5 }
        val bucket = path.count { it }
        return PlinkoResult(path, bucket, multipliers(risk)[bucket])
    }

    fun multipliers(risk: PlinkoRisk): List<Double> = when (risk) {
        PlinkoRisk.LOW -> listOf(5.0, 2.0, 1.3, 1.05, 0.9, 0.75, 0.6, 0.75, 0.9, 1.05, 1.3, 2.0, 5.0)
        PlinkoRisk.MEDIUM -> listOf(12.0, 4.0, 2.0, 1.2, 0.7, 0.4, 0.25, 0.4, 0.7, 1.2, 2.0, 4.0, 12.0)
        PlinkoRisk.HIGH -> listOf(45.0, 12.0, 4.0, 1.5, 0.5, 0.2, 0.1, 0.2, 0.5, 1.5, 4.0, 12.0, 45.0)
    }
}
