package com.vircas.mobile

import android.app.Application
import androidx.room.Room
import com.vircas.mobile.core.data.AppDatabase
import com.vircas.mobile.core.data.FairnessRepository
import com.vircas.mobile.core.data.GameHistoryRepository
import com.vircas.mobile.core.data.InventoryRepository
import com.vircas.mobile.core.data.SettingsRepository
import com.vircas.mobile.core.game.GameLedger
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
        val wallet = WalletRepository(this)
        val progression = ProgressionRepository(this)
        val history = GameHistoryRepository(db.gameHistoryDao())
        val fairness = FairnessRepository(db.fairnessRoundDao())
        container = AppContainer(
            walletRepository = wallet,
            settingsRepository = SettingsRepository(this),
            historyRepository = history,
            inventoryRepository = InventoryRepository(db.inventoryDao()),
            progressionRepository = progression,
            fairnessRepository = fairness,
            gameLedger = GameLedger(wallet, history, progression, fairness)
        )
    }
}

data class AppContainer(
    val walletRepository: WalletRepository,
    val settingsRepository: SettingsRepository,
    val historyRepository: GameHistoryRepository,
    val inventoryRepository: InventoryRepository,
    val progressionRepository: ProgressionRepository,
    val fairnessRepository: FairnessRepository,
    val gameLedger: GameLedger
)
