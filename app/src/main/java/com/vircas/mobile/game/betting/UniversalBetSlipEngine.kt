package com.vircas.mobile.game.betting

import com.vircas.mobile.core.random.RandomProvider
import com.vircas.mobile.game.engines.EsportsBettingEngine
import com.vircas.mobile.game.engines.EsportsEvent
import com.vircas.mobile.game.engines.Horse
import com.vircas.mobile.game.engines.HorseRace
import com.vircas.mobile.game.engines.HorseRacingEngine
import com.vircas.mobile.game.engines.MarketSelection
import com.vircas.mobile.game.engines.SportsBettingEngine
import com.vircas.mobile.game.engines.VirtualEvent
import kotlin.math.round

sealed interface UniversalBetSelection {
    val id: String
    val eventId: String
    val label: String
    val odds: Double

    data class Sports(
        val event: VirtualEvent,
        val market: MarketSelection
    ) : UniversalBetSelection {
        override val id: String = market.id
        override val eventId: String = market.eventId
        override val label: String = "${event.home} vs ${event.away} · ${market.label}"
        override val odds: Double = market.odds
    }

    data class Esports(
        val event: EsportsEvent,
        val market: MarketSelection
    ) : UniversalBetSelection {
        override val id: String = market.id
        override val eventId: String = market.eventId
        override val label: String = "${event.teamA} vs ${event.teamB} · ${market.label}"
        override val odds: Double = market.odds
    }

    data class HorseWin(
        val race: HorseRace,
        val horse: Horse
    ) : UniversalBetSelection {
        override val id: String = "${race.id}:horse:${horse.id}"
        override val eventId: String = race.id
        override val label: String = "${horse.name} to win"
        override val odds: Double = horse.odds
    }
}

data class UniversalBetSlip(
    val selections: List<UniversalBetSelection>,
    val stake: Long
) {
    init {
        require(selections.isNotEmpty())
        require(stake > 0L)
        require(selections.map { it.eventId }.distinct().size == selections.size) {
            "A slip may contain only one selection per event"
        }
    }

    val combinedOdds: Double = round2(selections.fold(1.0) { acc, selection -> acc * selection.odds })
    val possiblePayout: Long = (stake * combinedOdds).toLong()
    val isExpress: Boolean get() = selections.size > 1
}

data class UniversalBetSettlement(
    val slip: UniversalBetSlip,
    val won: Boolean,
    val payoutMultiplier: Double,
    val resultLines: List<String>,
    val winningSelectionIds: Set<String>
) {
    val payout: Long get() = (slip.stake * payoutMultiplier).toLong()
}

class UniversalBetSlipEngine(private val random: RandomProvider) {
    fun create(selections: List<UniversalBetSelection>, stake: Long): UniversalBetSlip =
        UniversalBetSlip(selections, stake)

    fun settle(slip: UniversalBetSlip): UniversalBetSettlement {
        val sportEngine = SportsBettingEngine(random)
        val esportsEngine = EsportsBettingEngine(random)
        val horseEngine = HorseRacingEngine(random)
        val winners = linkedSetOf<String>()
        val lines = mutableListOf<String>()

        slip.selections.forEach { selection ->
            when (selection) {
                is UniversalBetSelection.Sports -> {
                    val result = sportEngine.simulate(selection.event)
                    winners += result.winnerSelectionId
                    lines += "${selection.event.home} ${result.homeScore}:${result.awayScore} ${selection.event.away}"
                }
                is UniversalBetSelection.Esports -> {
                    val result = esportsEngine.simulate(selection.event)
                    winners += result.winningSelectionIds
                    lines += "${selection.event.teamA} ${result.mapsA}:${result.mapsB} ${selection.event.teamB} · map1 ${result.mapOneWinner}"
                }
                is UniversalBetSelection.HorseWin -> {
                    val result = horseEngine.simulate(selection.race)
                    val winner = selection.race.horses.first { it.id == result.winnerId }
                    winners += "${selection.race.id}:horse:${result.winnerId}"
                    lines += "${selection.race.id} · ${winner.name} won"
                }
            }
        }

        val won = slip.selections.all { it.id in winners }
        return UniversalBetSettlement(
            slip = slip,
            won = won,
            payoutMultiplier = if (won) slip.combinedOdds else 0.0,
            resultLines = lines,
            winningSelectionIds = winners
        )
    }
}

private fun round2(value: Double): Double = round(value * 100.0) / 100.0
