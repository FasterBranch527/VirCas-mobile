package com.vircas.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vircas.mobile.VirCasApplication
import com.vircas.mobile.core.data.FairnessRoundEntity
import com.vircas.mobile.core.data.GameHistoryEntity
import com.vircas.mobile.core.data.InventoryItemEntity
import com.vircas.mobile.core.data.UserSettings
import com.vircas.mobile.core.game.RoundReceipt
import com.vircas.mobile.core.progression.DailyRewardClaim
import com.vircas.mobile.core.progression.UserProgress
import com.vircas.mobile.core.random.RandomProvider
import com.vircas.mobile.core.random.SecureRandomProvider
import com.vircas.mobile.core.random.SeededRandomProvider
import com.vircas.mobile.core.wallet.WalletRepository
import com.vircas.mobile.game.engines.CaseDefinition
import com.vircas.mobile.game.engines.CaseOpeningResult
import com.vircas.mobile.game.engines.CasesEngine
import com.vircas.mobile.game.engines.MarketSelection
import com.vircas.mobile.game.engines.SimulatedEventResult
import com.vircas.mobile.game.engines.SportsBettingEngine
import com.vircas.mobile.game.engines.VirtualEvent
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ResolvedPlay(val multiplier: Double, val result: String, val details: String = "")

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as VirCasApplication).container
    private var seededRandom: SeededRandomProvider? = null
    private var seededRandomSeed: Long? = null

    val balance: StateFlow<Long> = container.walletRepository.balance.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WalletRepository.STARTING_BALANCE
    )

    val settings: StateFlow<UserSettings> = container.settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserSettings()
    )

    val progress: StateFlow<UserProgress> = container.progressionRepository.progress.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserProgress()
    )

    val inventory: StateFlow<List<InventoryItemEntity>> = container.inventoryRepository.items.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val history: StateFlow<List<GameHistoryEntity>> = container.historyRepository.recent(30).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val fairness: StateFlow<List<FairnessRoundEntity>> = container.fairnessRepository.recent(50).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    fun randomProvider(): RandomProvider {
        val current = settings.value
        if (current.secureRng) return SecureRandomProvider()
        if (seededRandom == null || seededRandomSeed != current.debugSeed) {
            seededRandomSeed = current.debugSeed
            seededRandom = SeededRandomProvider(current.debugSeed)
        }
        return requireNotNull(seededRandom)
    }

    fun completeOnboarding() = viewModelScope.launch { container.settingsRepository.completeOnboarding() }

    fun claimDailyReward(onResult: (DailyRewardClaim?) -> Unit = {}) = viewModelScope.launch {
        val claim = container.progressionRepository.claimDailyReward()
        if (claim != null) container.walletRepository.credit(claim.amount)
        onResult(claim)
    }

    fun playResolved(
        game: String,
        stake: Long,
        resolver: (RandomProvider) -> ResolvedPlay,
        onResult: (RoundReceipt?) -> Unit = {}
    ) = viewModelScope.launch {
        val wager = container.gameLedger.begin(game, stake)
        if (wager == null) {
            onResult(null)
            return@launch
        }
        try {
            val play = resolver(randomProvider())
            val receipt = container.gameLedger.settle(
                wager = wager,
                multiplier = play.multiplier,
                result = play.result,
                details = play.details,
                generatedSeed = fairnessSeed(),
                clientSeed = settings.value.clientSeed
            )
            onResult(receipt)
        } catch (error: Throwable) {
            container.gameLedger.cancel(wager)
            throw error
        }
    }

    fun openCase(definition: CaseDefinition, onResult: (CaseOpeningResult?) -> Unit = {}) = viewModelScope.launch {
        val wager = container.gameLedger.begin("Cases", definition.cost)
        if (wager == null) {
            onResult(null)
            return@launch
        }
        try {
            val result = CasesEngine(randomProvider()).open(definition)
            val item = result.item
            container.inventoryRepository.add(
                InventoryItemEntity(
                    id = UUID.randomUUID().toString(),
                    templateId = item.id,
                    name = item.name,
                    weaponCategory = item.weaponCategory,
                    rarity = item.rarity.name,
                    marketValue = item.marketValue,
                    previewKey = item.previewKey,
                    acquiredAt = System.currentTimeMillis()
                )
            )
            container.progressionRepository.recordCaseOpen()
            container.gameLedger.settle(
                wager,
                multiplier = 0.0,
                result = item.name,
                details = "${definition.title} · ${item.rarity.name}",
                generatedSeed = fairnessSeed(),
                clientSeed = settings.value.clientSeed
            )
            onResult(result)
        } catch (error: Throwable) {
            container.gameLedger.cancel(wager)
            throw error
        }
    }

    fun placeVirtualBet(
        event: VirtualEvent,
        selection: MarketSelection,
        stake: Long,
        onResult: (Pair<RoundReceipt, SimulatedEventResult>?) -> Unit = {}
    ) = viewModelScope.launch {
        if (selection.eventId != event.id) {
            onResult(null)
            return@launch
        }
        val wager = container.gameLedger.begin("Virtual ${event.sport.name.lowercase()}", stake)
        if (wager == null) {
            onResult(null)
            return@launch
        }
        try {
            val result = SportsBettingEngine(randomProvider()).simulate(event)
            val multiplier = if (result.winnerSelectionId == selection.id) selection.odds else 0.0
            container.progressionRepository.recordVirtualBet()
            val receipt = container.gameLedger.settle(
                wager,
                multiplier,
                "${result.homeScore}:${result.awayScore}",
                "${event.home} vs ${event.away} · ${selection.label}",
                fairnessSeed(),
                settings.value.clientSeed
            )
            onResult(receipt to result)
        } catch (error: Throwable) {
            container.gameLedger.cancel(wager)
            throw error
        }
    }

    fun sellInventoryItem(id: String, onResult: (Long?) -> Unit = {}) = viewModelScope.launch {
        val item = container.inventoryRepository.find(id)
        if (item == null) {
            onResult(null)
            return@launch
        }
        if (container.inventoryRepository.remove(id)) {
            container.walletRepository.credit(item.marketValue)
            onResult(item.marketValue)
        } else {
            onResult(null)
        }
    }

    fun setSound(value: Boolean) = viewModelScope.launch { container.settingsRepository.setSound(value) }
    fun setVibration(value: Boolean) = viewModelScope.launch { container.settingsRepository.setVibration(value) }
    fun setAnimations(value: Boolean) = viewModelScope.launch { container.settingsRepository.setAnimations(value) }
    fun setReducedMotion(value: Boolean) = viewModelScope.launch { container.settingsRepository.setReducedMotion(value) }
    fun setDarkMode(value: Boolean) = viewModelScope.launch { container.settingsRepository.setDarkMode(value) }
    fun setSecureRng(value: Boolean) = viewModelScope.launch { container.settingsRepository.setSecureRng(value) }
    fun setDeveloperDiagnostics(value: Boolean) = viewModelScope.launch { container.settingsRepository.setDeveloperDiagnostics(value) }
    fun setClientSeed(value: String) = viewModelScope.launch { container.settingsRepository.setClientSeed(value) }

    fun resetLocalAccount() = viewModelScope.launch {
        container.walletRepository.reset()
        container.settingsRepository.reset()
        container.progressionRepository.reset()
        container.inventoryRepository.clear()
        container.historyRepository.clear()
        container.fairnessRepository.clear()
    }

    private fun fairnessSeed(): String = if (settings.value.secureRng) {
        UUID.randomUUID().toString().replace("-", "")
    } else {
        "debug-${settings.value.debugSeed}"
    }
}
