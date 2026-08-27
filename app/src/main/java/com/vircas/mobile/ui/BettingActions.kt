package com.vircas.mobile.ui

import androidx.lifecycle.viewModelScope
import com.vircas.mobile.VirCasApplication
import com.vircas.mobile.core.game.RoundReceipt
import com.vircas.mobile.game.engines.BetSlipEngine
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
) = viewModelScope.launch {
    val container = getApplication<VirCasApplication>().container
    val byId = events.associateBy { it.id }
    val valid = selections.isNotEmpty() && selections.all { it.eventId in byId }
    if (!valid) {
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
        val engine = SportsBettingEngine(randomProvider())
        val results = selections.map { selection -> engine.simulate(requireNotNull(byId[selection.eventId])) }
        val winners = results.map { it.winnerSelectionId }.toSet()
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
