package com.vircas.mobile.core.wallet

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.walletDataStore by preferencesDataStore("wallet")

class WalletRepository(private val context: Context) {
    private val balanceKey = longPreferencesKey("balance")
    val balance: Flow<Long> = context.walletDataStore.data.map { it[balanceKey] ?: STARTING_BALANCE }

    suspend fun debit(amount: Long): Boolean {
        if (amount <= 0) return false
        var accepted = false
        context.walletDataStore.edit { prefs ->
            val current = prefs[balanceKey] ?: STARTING_BALANCE
            if (current >= amount) {
                prefs[balanceKey] = current - amount
                accepted = true
            }
        }
        return accepted
    }

    suspend fun credit(amount: Long) {
        if (amount <= 0) return
        context.walletDataStore.edit { prefs ->
            val current = prefs[balanceKey] ?: STARTING_BALANCE
            val room = Long.MAX_VALUE - current
            prefs[balanceKey] = current + amount.coerceAtMost(room)
        }
    }

    suspend fun reset() = context.walletDataStore.edit { it[balanceKey] = STARTING_BALANCE }

    companion object { const val STARTING_BALANCE = 100_000L }
}
