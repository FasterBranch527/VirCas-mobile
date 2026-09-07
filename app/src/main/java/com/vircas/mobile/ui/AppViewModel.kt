package com.vircas.mobile.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vircas.mobile.VirCasApplication
import com.vircas.mobile.core.data.FairnessRoundEntity
import com.vircas.mobile.core.data.GameHistoryEntity
import com.vircas.mobile.core.data.InventoryItemEntity
import com.vircas.mobile.core.data.UserSettings
import com.vircas.mobile.core.game.ActiveWager
import com.vircas.mobile.core.game.RoundMemory
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class ResolvedPlay(val multiplier: Double, val result: String, val details: String = "")
private data class RoundRandom(val generatedSeed: String, val clientSeed: String, val provider: RandomProvider)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as VirCasApplication).container
    private var seededRandom: SeededRandomProvider? = null
    private var seededRandomSeed: Long? = null
    private var debugRoundCounter = 0L
    private var interactiveRound: RoundRandom? = null
    private var interactiveWagerId: String? = null
    private val roundRandoms = mutableMapOf<String, RoundRandom>()
    private val roundMemory = RoundMemory()
    private val taskScopes = mutableMapOf<String, CoroutineScope>()
    private val startingGames = mutableSetOf<String>()
    private val pendingWrites = linkedMapOf<String, suspend () -> Unit>()
    private val writeMutex = Mutex()
    private var retrying = false
    internal var roundGeneration by mutableIntStateOf(0)
        private set
    private val readyState = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = readyState
    private val saveErrorState = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = saveErrorState

    val balance: StateFlow<Long> = container.walletRepository.balance.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletRepository.STARTING_BALANCE)
    val settings: StateFlow<UserSettings> = container.settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val progress: StateFlow<UserProgress> = container.progressionRepository.progress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProgress())
    val inventory: StateFlow<List<InventoryItemEntity>> = container.inventoryRepository.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history: StateFlow<List<GameHistoryEntity>> = container.historyRepository.recent(100).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val fairness: StateFlow<List<FairnessRoundEntity>> = container.fairnessRepository.recent(100).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { initializeRounds() }

    private fun initializeRounds() = viewModelScope.launch {
        try {
            container.gameLedger.recoverInterruptedRounds()
            readyState.value = true
        } catch (error: Exception) {
            failedWrite("initialize", error) {
                container.gameLedger.recoverInterruptedRounds()
                readyState.value = true
            }
        }
    }

    internal fun <T> retainRound(key: String, initializer: () -> T): T = roundMemory.remember(key, initializer)

    internal fun roundTaskScope(key: String): CoroutineScope = taskScopes.getOrPut(key) {
        CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
    }

    internal fun hasRoundTask(key: String): Boolean = taskScopes[key]?.coroutineContext?.get(Job)?.children?.any { it.isActive } == true

    fun randomProvider(wager: ActiveWager? = null): RandomProvider {
        if (wager != null) return requireNotNull(roundRandoms[wager.id]) { "No random stream for active wager" }.provider
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
    fun claimAchievement(achievement: Achievement, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch { onResult(container.progressionRepository.claimAchievement(achievement)) }
    fun dailyMissions(progress: UserProgress): List<DailyMission> = container.progressionRepository.missions(progress)
    fun achievements(progress: UserProgress): List<Achievement> = container.progressionRepository.achievements(progress)

    fun beginWager(game: String, stake: Long, onResult: (ActiveWager?) -> Unit): Job {
        if (!readyState.value || saveErrorState.value != null) {
            return viewModelScope.launch { onResult(null) }
        }
        // Ignore an extra tap while the first reservation is still being saved.
        if (!startingGames.add(game)) return viewModelScope.launch { }
        val generation = roundGeneration
        return viewModelScope.launch {
            try {
                val rng = roundRandom()
                val wager = container.gameLedger.begin(game, stake, rng.generatedSeed, rng.clientSeed)
                if (generation != roundGeneration) {
                    if (wager != null) container.gameLedger.cancel(wager)
                    return@launch
                }
                if (wager != null) {
                    roundRandoms[wager.id] = rng
                    interactiveRound = rng
                    interactiveWagerId = wager.id
                }
                deliverResult { onResult(wager) }
            } catch (error: Exception) {
                if (generation == roundGeneration) {
                    reportError(error)
                    onResult(null)
                }
            } finally { startingGames.remove(game) }
        }
    }

    fun increaseWager(wager: ActiveWager, additionalStake: Long, onResult: (ActiveWager?) -> Unit) = viewModelScope.launch {
        val generation = roundGeneration
        val result = runCatching { container.gameLedger.increase(wager, additionalStake) }
        if (generation == roundGeneration) {
            result.exceptionOrNull()?.let(::reportError)
            onResult(result.getOrNull())
        }
    }

    fun checkpointWager(wager: ActiveWager, multiplier: Double, result: String, details: String = "", terminal: Boolean = true) =
        roundWrite("checkpoint:${wager.id}") { container.gameLedger.checkpoint(wager, multiplier, result, details, terminal) }

    fun settleWager(wager: ActiveWager, multiplier: Double, result: String, details: String = "", onResult: (RoundReceipt?) -> Unit = {}) =
        roundWrite("settle:${wager.id}") {
            flushCheckpoint(wager.id)
            val receipt = container.gameLedger.settle(wager, multiplier, result, details)
            clearRoundRandom(wager.id)
            deliverResult { onResult(receipt) }
        }

    fun cancelWager(wager: ActiveWager) = roundWrite("cancel:${wager.id}") {
        flushCheckpoint(wager.id)
        container.gameLedger.cancel(wager)
        clearRoundRandom(wager.id)
    }

    private suspend fun flushCheckpoint(id: String) {
        val key = "checkpoint:$id"
        pendingWrites[key]?.let { retry -> retry(); pendingWrites.remove(key) }
    }

    private fun roundWrite(key: String, write: suspend () -> Unit): Job {
        val generation = roundGeneration
        return viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                writeMutex.withLock {
                    if (generation != roundGeneration) return@launch
                    withContext(NonCancellable) { write() }
                }
                if (generation == roundGeneration) {
                    pendingWrites.remove(key)
                    if (pendingWrites.isEmpty()) saveErrorState.value = null
                }
            } catch (error: Exception) {
                if (generation == roundGeneration) failedWrite(key, error, write)
            }
        }
    }

    private fun deliverResult(deliver: () -> Unit) {
        // A presentation exception must never be retried as a financial transaction.
        try { deliver() } catch (error: Exception) { Log.e("AppViewModel", "Round presentation failed", error) }
    }

    private fun failedWrite(key: String, error: Exception, retry: suspend () -> Unit) {
        pendingWrites[key] = retry
        reportError(error)
    }

    private fun reportError(error: Exception) {
        Log.w("AppViewModel", "Local save failed", error)
        saveErrorState.value = "Could not save local data. Please retry before continuing."
    }

    fun retrySaving() = viewModelScope.launch {
        if (retrying) return@launch
        retrying = true
        val generation = roundGeneration
        try {
            writeMutex.withLock {
                for ((key, retry) in pendingWrites.toMap()) {
                    if (generation != roundGeneration) return@launch
                    withContext(NonCancellable) { retry() }
                    pendingWrites.remove(key)
                }
                if (generation == roundGeneration) saveErrorState.value = null
            }
        } catch (error: Exception) { if (generation == roundGeneration) reportError(error) }
        finally { retrying = false }
    }

    private suspend fun failedReservation(wager: ActiveWager, error: Exception) {
        try { container.gameLedger.cancel(wager) }
        catch (saveFailure: Exception) {
            failedWrite("cancel:${wager.id}", saveFailure) { container.gameLedger.cancel(wager) }
        }
        Log.w("AppViewModel", "Round resolution failed", error)
    }

    fun playResolved(game: String, stake: Long, resolver: (RandomProvider) -> ResolvedPlay, onResult: (RoundReceipt?) -> Unit = {}) = viewModelScope.launch {
        if (!readyState.value || saveErrorState.value != null) { onResult(null); return@launch }
        val generation = roundGeneration
        var wager: ActiveWager? = null
        val result = runCatching {
            val rng = roundRandom()
            wager = container.gameLedger.begin(game, stake, rng.generatedSeed, rng.clientSeed)
            val active = wager ?: return@runCatching null
            if (generation != roundGeneration) { container.gameLedger.cancel(active); return@runCatching null }
            val play = resolver(rng.provider)
            container.gameLedger.settle(active, play.multiplier, play.result, play.details)
        }
        result.exceptionOrNull()?.let { error -> wager?.let { failedReservation(it, error) } }
        if (generation == roundGeneration) onResult(result.getOrNull())
    }

    fun openCase(definition: CaseDefinition, onResult: (CaseOpeningResult?) -> Unit = {}) = viewModelScope.launch {
        if (!readyState.value || saveErrorState.value != null) { onResult(null); return@launch }
        val generation = roundGeneration
        var wager: ActiveWager? = null
        val opened = runCatching {
            val rng = roundRandom()
            wager = container.gameLedger.begin("Cases", definition.cost, rng.generatedSeed, rng.clientSeed)
            val active = wager ?: return@runCatching null
            if (generation != roundGeneration) { container.gameLedger.cancel(active); return@runCatching null }
            val result = CasesEngine(rng.provider).open(definition)
            val item = result.item
            container.gameLedger.settle(
                active, 0.0, item.name, "${definition.title} · ${item.rarity.name}",
                item = InventoryItemEntity(
                    id = "drop-${active.id}", templateId = item.id, name = item.name,
                    weaponCategory = item.weaponCategory, rarity = item.rarity.name,
                    marketValue = item.marketValue, previewKey = item.previewKey,
                    acquiredAt = System.currentTimeMillis()
                )
            )
            result
        }
        opened.exceptionOrNull()?.let { error -> wager?.let { failedReservation(it, error) } }
        if (generation == roundGeneration) onResult(opened.getOrNull())
    }

    fun placeVirtualBet(event: VirtualEvent, selection: MarketSelection, stake: Long, onResult: (Pair<RoundReceipt, SimulatedEventResult>?) -> Unit = {}) = viewModelScope.launch {
        if (!readyState.value || saveErrorState.value != null || selection.eventId != event.id) { onResult(null); return@launch }
        val generation = roundGeneration
        var wager: ActiveWager? = null
        val settled = runCatching {
            val rng = roundRandom()
            wager = container.gameLedger.begin("Virtual ${event.sport.name.lowercase()}", stake, rng.generatedSeed, rng.clientSeed)
            val active = wager ?: return@runCatching null
            if (generation != roundGeneration) { container.gameLedger.cancel(active); return@runCatching null }
            val result = SportsBettingEngine(rng.provider).simulate(event)
            val multiplier = if (result.winnerSelectionId == selection.id) selection.odds else 0.0
            val receipt = container.gameLedger.settle(active, multiplier, "${result.homeScore}:${result.awayScore}", "${event.home} vs ${event.away} · ${selection.label}")
            receipt to result
        }
        settled.exceptionOrNull()?.let { error -> wager?.let { failedReservation(it, error) } }
        if (generation == roundGeneration) onResult(settled.getOrNull())
    }

    fun placeUniversalBetSlip(selections: List<UniversalBetSelection>, stake: Long, onResult: (Pair<RoundReceipt, UniversalBetSettlement>?) -> Unit = {}) = viewModelScope.launch {
        if (!readyState.value || saveErrorState.value != null || selections.isEmpty() || stake <= 0L) { onResult(null); return@launch }
        val generation = roundGeneration
        var wager: ActiveWager? = null
        val settled = runCatching {
            val rng = roundRandom()
            wager = container.gameLedger.begin(if (selections.size > 1) "Virtual Express" else "Virtual Single", stake, rng.generatedSeed, rng.clientSeed)
            val active = wager ?: return@runCatching null
            if (generation != roundGeneration) { container.gameLedger.cancel(active); return@runCatching null }
            val engine = UniversalBetSlipEngine(rng.provider)
            val result = engine.settle(engine.create(selections, stake))
            val receipt = container.gameLedger.settle(active, result.payoutMultiplier, if (result.won) "Bet slip won" else "Bet slip lost", result.resultLines.joinToString(" | "))
            receipt to result
        }
        settled.exceptionOrNull()?.let { error -> wager?.let { failedReservation(it, error) } }
        if (generation == roundGeneration) onResult(settled.getOrNull())
    }

    fun sellInventoryItem(id: String, onResult: (Long?) -> Unit = {}) = viewModelScope.launch {
        val result = runCatching { container.gameLedger.sellInventoryItem(id) }
        result.exceptionOrNull()?.let(::reportError)
        onResult(result.getOrNull())
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

    fun resetLocalAccount(): Job {
        readyState.value = false
        roundGeneration++
        taskScopes.values.forEach { it.cancel() }
        taskScopes.clear()
        roundMemory.clear()
        roundRandoms.clear()
        interactiveRound = null
        interactiveWagerId = null
        pendingWrites.clear()
        startingGames.clear()
        saveErrorState.value = null
        debugRoundCounter = 0L
        return roundWrite("reset") {
            container.gameLedger.resetLocalAccount()
            container.settingsRepository.reset()
            readyState.value = true
        }
    }

    private suspend fun roundRandom(): RoundRandom {
        val current = container.settingsRepository.settings.first()
        val generatedSeed = if (current.secureRng) UUID.randomUUID().toString().replace("-", "")
        else "debug-${current.debugSeed}-${debugRoundCounter++}"
        val material = "$generatedSeed|${current.clientSeed}"
        return RoundRandom(generatedSeed, current.clientSeed, SeededRandomProvider(seedToLong(material)))
    }

    private fun seedToLong(material: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return digest.take(8).fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xffL) }
    }

    private fun clearRoundRandom(id: String) {
        roundRandoms.remove(id)
        if (interactiveWagerId == id) { interactiveRound = null; interactiveWagerId = null }
    }
}
