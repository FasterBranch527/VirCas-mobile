package com.vircas.mobile.core.wallet

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.vircas.mobile.core.data.AppDatabase
import com.vircas.mobile.core.data.WalletRow
import com.vircas.mobile.core.game.WagerRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private val Context.walletDataStore by preferencesDataStore("wallet")

/** Room is authoritative. The old DataStore balance is imported once without resetting it. */
class WalletRepository(private val context: Context, internal val database: AppDatabase) {
    private val dao get() = database.ledgerDao()
    private val balanceKey = longPreferencesKey("balance")
    val balance: Flow<Long> = flow {
        ensureInitialized()
        emitAll(dao.observeBalance().map { requireNotNull(it) { "Wallet is not initialized" } })
    }
    suspend fun ensureInitialized() {
        if (dao.wallet() != null) return
        val previous = context.walletDataStore.data.first()[balanceKey] ?: STARTING_BALANCE
        check(previous >= 0L) { "Invalid saved wallet balance" }
        database.withTransaction { dao.initializeWallet(WalletRow(balance = previous)) }
    }
    suspend fun debit(amount: Long): Boolean {
        if (amount <= 0L) return false
        ensureInitialized()
        return database.withTransaction {
            val current = requireNotNull(dao.wallet())
            if (current.balance < amount) false
            else { dao.updateWallet(current.copy(balance = current.balance - amount)); true }
        }
    }
    suspend fun credit(amount: Long) {
        if (amount <= 0L) return
        ensureInitialized()
        database.withTransaction {
            val current = requireNotNull(dao.wallet())
            dao.updateWallet(current.copy(balance = WagerRules.credit(current.balance, amount)))
        }
    }
    suspend fun reset() {
        ensureInitialized()
        database.withTransaction { dao.updateWallet(WalletRow(balance = STARTING_BALANCE)) }
    }
    companion object { const val STARTING_BALANCE = 100_000L }
}
