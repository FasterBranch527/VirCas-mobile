package com.vircas.mobile

import android.app.Application
import androidx.room.Room
import com.vircas.mobile.core.data.AppDatabase
import com.vircas.mobile.core.data.FairnessRepository
import com.vircas.mobile.core.data.GameHistoryRepository
import com.vircas.mobile.core.data.InventoryRepository
import com.vircas.mobile.core.data.SettingsRepository
import com.vircas.mobile.core.progression.ProgressionRepository
import com.vircas.mobile.core.wallet.WalletRepository

class VirCasApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "vircas.db")
            .fallbackToDestructiveMigration()
            .build()
        container = AppContainer(
            walletRepository = WalletRepository(this),
            settingsRepository = SettingsRepository(this),
            historyRepository = GameHistoryRepository(db.gameHistoryDao()),
            inventoryRepository = InventoryRepository(db.inventoryDao()),
            progressionRepository = ProgressionRepository(this),
            fairnessRepository = FairnessRepository(db.fairnessRoundDao())
        )
    }
}

data class AppContainer(
    val walletRepository: WalletRepository,
    val settingsRepository: SettingsRepository,
    val historyRepository: GameHistoryRepository,
    val inventoryRepository: InventoryRepository,
    val progressionRepository: ProgressionRepository,
    val fairnessRepository: FairnessRepository
)
