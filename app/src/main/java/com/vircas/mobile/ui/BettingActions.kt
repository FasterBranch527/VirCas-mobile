package com.vircas.mobile.ui

import androidx.lifecycle.viewModelScope
import com.vircas.mobile.VirCasApplication
import com.vircas.mobile.core.game.RoundReceipt
import com.vircas.mobile.game.engines.BetSlipEngine
import com.vircas.mobile.game.engines.EsportsBettingEngine
import com.vircas.mobile.game.engines.EsportsEvent
import com.vircas.mobile.game.engines.HorseRace
import com.vircas.mobile.game.engines.HorseRacingEngine
import com.vircas.mobile.game.engines.MarketSelection
import com.vircas.mobile.game.engines.SportsBettingEngine
import com.vircas.mobile.game.engines.VirtualEvent
import java.util.UUID
import kotlinx.coroutines.launch

fun AppViewModel.placeVirtualBetSlip(
    events: List<VirtualEvent>,
    selections: List<MarketSelection>,
    stake: Long,
    onResult: (RoundReceipt?) -> Unit = {}
) = placeMixedBetSlip(events, emptyList(), null, selections, stake, onResult)

fun AppViewModel.placeMixedBetSlip(
    sportsEvents: List<VirtualEvent>,
    esportsEvents: List<EsportsEvent>,
    horseRace: HorseRace?,
    selections: List<MarketSelection>,
    stake: Long,
    onResult: (RoundReceipt?) -> Unit = {}
) = viewModelScope.launch {
    val container = getApplication<VirCasApplication>().container
    val sportsById = sportsEvents.associateBy { it.id }
    val esportsById = esportsEvents.associateBy { it.id }
    val knownEventIds = sportsById.keys + esportsById.keys + listOfNotNull(horseRace?.id)
    if (selections.isEmpty() || selections.any { it.eventId !in knownEventIds }) {
        onResult(null)
        return@launch
    }

    val slip = runCatching { BetSlipEngine().create(selections, stake) }.getOrNull()
    if (slip == null) {
        onResult(null)
        return@launch
    }

    val wager = container.gameLedger.begin(if (selections.size == 1) "Virtual Bet" else "Virtual Accumulator", stake)
    if (wager == null) {
        onResult(null)
        return@launch
    }

    runCatching {
        val random = randomProvider()
        val sportsEngine = SportsBettingEngine(random)
        val esportsEngine = EsportsBettingEngine(random)
        val horseEngine = HorseRacingEngine(random)
        val winners = mutableSetOf<String>()

        selections.forEach { selection ->
            sportsById[selection.eventId]?.let { event ->
                winners += sportsEngine.simulate(event).winnerSelectionId
            }
            esportsById[selection.eventId]?.let { event ->
                winners += esportsEngine.simulate(event).winningSelectionIds
            }
            if (horseRace != null && selection.eventId == horseRace.id) {
                val result = horseEngine.simulate(horseRace)
                winners += "${horseRace.id}:horse:${result.winnerId}"
            }
        }

        repeat(selections.size) { container.progressionRepository.recordVirtualBet() }
        val multiplier = if (selections.all { it.id in winners }) slip.combinedOdds else 0.0
        val resultText = if (multiplier > 0.0) "Bet slip won" else "Bet slip lost"
        container.gameLedger.settle(
            wager,
            multiplier,
            resultText,
            selections.joinToString(" · ") { "${it.label} @ ${it.odds}" },
            if (settings.value.secureRng) UUID.randomUUID().toString().replace("-", "") else "debug-${settings.value.debugSeed}",
            settings.value.clientSeed
        )
    }.onSuccess(onResult).onFailure {
        container.gameLedger.cancel(wager)
        onResult(null)
    }
}
