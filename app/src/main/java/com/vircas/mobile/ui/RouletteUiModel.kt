package com.vircas.mobile.ui

import androidx.compose.ui.graphics.Color
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.game.engines.RouletteBet
import com.vircas.mobile.game.engines.RouletteColor
import com.vircas.mobile.game.engines.RouletteEngine

internal val rouletteWheelOrder = listOf(
    0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
    5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
)

internal val rouletteChipValues = listOf(10L, 50L, 100L, 500L, 1_000L, 5_000L)

internal data class RoulettePendingRound(
    val wager: ActiveWager,
    val number: Int,
    val color: RouletteColor,
    val payout: Long,
    val payoutMultiplier: Double,
    val bets: Map<RouletteBet, List<Long>>
)

internal fun rouletteBetWon(number: Int, bet: RouletteBet): Boolean = when (bet) {
    is RouletteBet.Number -> number == bet.number
    is RouletteBet.Color -> number != 0 && RouletteEngine.colorOf(number) == bet.color
    RouletteBet.Odd -> number != 0 && number % 2 == 1
    RouletteBet.Even -> number != 0 && number % 2 == 0
    RouletteBet.Low -> number in 1..18
    RouletteBet.High -> number in 19..36
    is RouletteBet.Dozen -> number in ((bet.index - 1) * 12 + 1)..(bet.index * 12)
    is RouletteBet.Column -> number != 0 && ((number - 1) % 3) + 1 == bet.index
}

internal fun rouletteBetSummary(bets: Map<RouletteBet, List<Long>>): String = bets.entries
    .sortedByDescending { it.value.sum() }
    .joinToString(" · ") { (bet, chips) -> "${rouletteBetName(bet)} ${formatRouletteVc(chips.sum())}" }

internal fun rouletteBetName(bet: RouletteBet): String = when (bet) {
    is RouletteBet.Number -> "#${bet.number}"
    is RouletteBet.Color -> bet.color.name
    RouletteBet.Odd -> "ODD"
    RouletteBet.Even -> "EVEN"
    RouletteBet.Low -> "1–18"
    RouletteBet.High -> "19–36"
    is RouletteBet.Dozen -> "${bet.index}${when (bet.index) { 1 -> "ST"; 2 -> "ND"; else -> "RD" }} 12"
    is RouletteBet.Column -> "COLUMN ${bet.index}"
}

internal fun rouletteChipColor(value: Long): Color = when (value) {
    10L -> Color(0xFF64748B)
    50L -> Color(0xFFD13B32)
    100L -> Color(0xFF258C48)
    500L -> Color(0xFF25282D)
    1_000L -> Color(0xFF7441A5)
    else -> Color(0xFFB17B1D)
}

internal fun rouletteNumberColor(number: Int): Color = when (RouletteEngine.colorOf(number)) {
    RouletteColor.RED -> Color(0xFFC52B27)
    RouletteColor.BLACK -> Color(0xFF171A18)
    RouletteColor.GREEN -> Color(0xFF137437)
}

internal fun formatRouletteVc(value: Long): String = "%,d VC".format(value)
internal fun shortRouletteChip(value: Long): String = if (value >= 1_000) "${value / 1_000}K" else value.toString()
