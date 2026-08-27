package com.vircas.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vircas.mobile.VirCasApplication
import com.vircas.mobile.core.data.FairnessRoundEntity
import com.vircas.mobile.core.data.GameHistoryEntity
import com.vircas.mobile.core.data.InventoryItemEntity
import com.vircas.mobile.core.data.UserSettings
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.core.game.RoundReceipt
import com.vircas.mobile.core.progression.Achievement
import com.vircas.mobile.core.progression.DailyMission
import com.vircas.mobile.core.progression.DailyRewardClaim
import com.vircas.mobile.core.progression.UserProgress
import com.vircas.mobile.core.random.RandomProvider
import com.vircas.mobile.core.random.SecureRandomProvider
import com.vircas.mobile.core.random.SeededRandomProvider
import com.vircas.mobile.core.wallet.WalletRepository
import com.vircas.mobile.game.betting.UniversalBetSelection
import com.vircas.mobile.game.betting.UniversalBetSettlement
import com.vircas.mobile.game.betting.UniversalBetSlipEngine
import com.vircas.mobile.game.engines.CaseDefinition
import com.vircas.mobile.game.engines.CaseOpeningResult
import com.vircas.mobile.game.engines.CasesEngine
import com.vircas.mobile.game.engines.MarketSelection
import com.vircas.mobile.game.engines.SimulatedEventResult
import com.vircas.mobile.game.engines.SportsBettingEngine
import com.vircas.mobile.game.engines.VirtualEvent
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ResolvedPlay(val multiplier: Double, val result: String, val details: String = "")
private data class RoundRandom(val generatedSeed: String, val provider: RandomProvider)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as VirCasApplication).container
    private var seededRandom: SeededRandomProvider? = null
    private var seededRandomSeed: Long? = null
    private var debugRoundCounter = 0L
    private var interactiveRound: RoundRandom? = null
    private var interactiveWagerId: String? = null

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
    val history: StateFlow<List<GameHistoryEntity>> = container.historyRepository.recent(100).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val fairness: StateFlow<List<FairnessRoundEntity>> = container.fairnessRepository.recent(100).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    /**
     * Multi-step screens call this after [beginWager], so they receive the exact provider derived
     * from the seed that will later be written to Fairness. Outside an active wager it is also
     * useful for non-round demo data generation.
     */
    fun randomProvider(): RandomProvider {
        interactiveRound?.let { return it.provider }
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

    fun claimMission(mission: DailyMission, onResult: (Long?) -> Unit = {}) = viewModelScope.launch {
        val coins = container.progressionRepository.claimMission(mission)
        if (coins != null) container.walletRepository.credit(coins)
        onResult(coins)
    }

    fun claimAchievement(achievement: Achievement, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(container.progressionRepository.claimAchievement(achievement))
    }

    fun dailyMissions(progress: UserProgress): List<DailyMission> = container.progressionRepository.missions(progress)
    fun achievements(progress: UserProgress): List<Achievement> = container.progressionRepository.achievements(progress)

    fun beginWager(game: String, stake: Long, onResult: (ActiveWager?) -> Unit) = viewModelScope.launch {
        val wager = container.gameLedger.begin(game, stake)
        if (wager != null) {
            interactiveRound = roundRandom()
            interactiveWagerId = wager.id
        }
        onResult(wager)
    }

    fun increaseWager(wager: ActiveWager, additionalStake: Long, onResult: (ActiveWager?) -> Unit) = viewModelScope.launch {
        onResult(container.gameLedger.increase(wager, additionalStake))
    }

    fun settleWager(
        wager: ActiveWager,
        multiplier: Double,
        result: String,
        details: String = "",
        onResult: (RoundReceipt?) -> Unit = {}
    ) = viewModelScope.launch {
        val matchingInteractive = interactiveWagerId == wager.id
        val roundRandom = if (matchingInteractive) interactiveRound ?: roundRandom() else roundRandom()
        runCatching {
            container.gameLedger.settle(
                wager,
                multiplier,
                result,
                details,
                generatedSeed = roundRandom.generatedSeed,
                clientSeed = settings.value.clientSeed
            )
        }.onSuccess(onResult).onFailure { onResult(null) }
        if (matchingInteractive) clearInteractiveRound()
    }

    fun cancelWager(wager: ActiveWager) = viewModelScope.launch {
        container.gameLedger.cancel(wager)
        if (interactiveWagerId == wager.id) clearInteractiveRound()
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
        runCatching {
            val roundRandom = roundRandom()
            val play = resolver(roundRandom.provider)
            container.gameLedger.settle(
                wager = wager,
                multiplier = play.multiplier,
                result = play.result,
                details = play.details,
                generatedSeed = roundRandom.generatedSeed,
                clientSeed = settings.value.clientSeed
            )
        }.onSuccess(onResult).onFailure {
            container.gameLedger.cancel(wager)
            onResult(null)
        }
    }

    fun openCase(definition: CaseDefinition, onResult: (CaseOpeningResult?) -> Unit = {}) = viewModelScope.launch {
        val wager = container.gameLedger.begin("Cases", definition.cost)
        if (wager == null) {
            onResult(null)
            return@launch
        }
        runCatching {
            val roundRandom = roundRandom()
            val result = CasesEngine(roundRandom.provider).open(definition)
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
                0.0,
                item.name,
                "${definition.title} · ${item.rarity.name}",
                roundRandom.generatedSeed,
                settings.value.clientSeed
            )
            result
        }.onSuccess(onResult).onFailure {
            container.gameLedger.cancel(wager)
            onResult(null)
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
        runCatching {
            val roundRandom = roundRandom()
            val result = SportsBettingEngine(roundRandom.provider).simulate(event)
            val multiplier = if (result.winnerSelectionId == selection.id) selection.odds else 0.0
            container.progressionRepository.recordVirtualBet()
            val receipt = container.gameLedger.settle(
                wager,
                multiplier,
                "${result.homeScore}:${result.awayScore}",
                "${event.home} vs ${event.away} · ${selection.label}",
                roundRandom.generatedSeed,
                settings.value.clientSeed
            )
            receipt to result
        }.onSuccess(onResult).onFailure {
            container.gameLedger.cancel(wager)
            onResult(null)
        }
    }

    fun placeUniversalBetSlip(
        selections: List<UniversalBetSelection>,
        stake: Long,
        onResult: (Pair<RoundReceipt, UniversalBetSettlement>?) -> Unit = {}
    ) = viewModelScope.launch {
        if (selections.isEmpty() || stake <= 0L) {
            onResult(null)
            return@launch
        }
        val wager = container.gameLedger.begin(
            if (selections.size > 1) "Virtual Express" else "Virtual Single",
            stake
        )
        if (wager == null) {
            onResult(null)
            return@launch
        }
        runCatching {
            val roundRandom = roundRandom()
            val engine = UniversalBetSlipEngine(roundRandom.provider)
            val slip = engine.create(selections, stake)
            val settlement = engine.settle(slip)
            container.progressionRepository.recordVirtualBet()
            val receipt = container.gameLedger.settle(
                wager = wager,
                multiplier = settlement.payoutMultiplier,
                result = if (settlement.won) "Bet slip won" else "Bet slip lost",
                details = settlement.resultLines.joinToString(" | "),
                generatedSeed = roundRandom.generatedSeed,
                clientSeed = settings.value.clientSeed
            )
            receipt to settlement
        }.onSuccess(onResult).onFailure {
            container.gameLedger.cancel(wager)
            onResult(null)
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
    fun setDebugSeed(value: Long) = viewModelScope.launch { container.settingsRepository.setDebugSeed(value) }
    fun setDeveloperDiagnostics(value: Boolean) = viewModelScope.launch { container.settingsRepository.setDeveloperDiagnostics(value) }
    fun setClientSeed(value: String) = viewModelScope.launch { container.settingsRepository.setClientSeed(value) }

    fun resetLocalAccount() = viewModelScope.launch {
        container.walletRepository.reset()
        container.settingsRepository.reset()
        container.progressionRepository.reset()
        container.inventoryRepository.clear()
        container.historyRepository.clear()
        container.fairnessRepository.clear()
        debugRoundCounter = 0L
        clearInteractiveRound()
    }

    private fun roundRandom(): RoundRandom {
        val current = settings.value
        val generatedSeed = if (current.secureRng) {
            UUID.randomUUID().toString().replace("-", "")
        } else {
            "debug-${current.debugSeed}-${debugRoundCounter++}"
        }
        val material = "$generatedSeed|${current.clientSeed}"
        return RoundRandom(generatedSeed, SeededRandomProvider(seedToLong(material)))
    }

    private fun seedToLong(material: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return digest.take(8).fold(0L) { acc, byte ->
            (acc shl 8) or (byte.toLong() and 0xffL)
        }
    }

    private fun clearInteractiveRound() {
        interactiveRound = null
        interactiveWagerId = null
    }
}
