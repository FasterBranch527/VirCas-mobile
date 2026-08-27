package com.vircas.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vircas.mobile.VirCasApplication
import com.vircas.mobile.core.data.FairnessRoundEntity
import com.vircas.mobile.core.data.GameHistoryEntity
import com.vircas.mobile.core.data.InventoryItemEntity
import com.vircas.mobile.core.data.UserSettings
import com.vircas.mobile.core.progression.DailyRewardClaim
import com.vircas.mobile.core.progression.UserProgress
import com.vircas.mobile.core.wallet.WalletRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as VirCasApplication).container

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

    fun completeOnboarding() = viewModelScope.launch { container.settingsRepository.completeOnboarding() }

    fun claimDailyReward(onResult: (DailyRewardClaim?) -> Unit = {}) = viewModelScope.launch {
        val claim = container.progressionRepository.claimDailyReward()
        if (claim != null) container.walletRepository.credit(claim.amount)
        onResult(claim)
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
}
