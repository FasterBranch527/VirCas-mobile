package com.vircas.mobile.core.game

import android.util.Log
import androidx.room.withTransaction
import com.vircas.mobile.core.data.FairnessRepository
import com.vircas.mobile.core.data.FairnessRoundEntity
import com.vircas.mobile.core.data.GameHistoryEntity
import com.vircas.mobile.core.data.GameHistoryRepository
import com.vircas.mobile.core.data.InventoryItemEntity
import com.vircas.mobile.core.data.WagerRow
import com.vircas.mobile.core.data.WalletRow
import com.vircas.mobile.core.wallet.WalletRepository
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class GameLedger(
    private val wallet: WalletRepository,
    @Suppress("UNUSED_PARAMETER") history: GameHistoryRepository,
    private val progression: RoundProgressSink,
    @Suppress("UNUSED_PARAMETER") fairness: FairnessRepository
) {
    private val db = wallet.database
    private val dao get() = db.ledgerDao()
    private val secureRandom = SecureRandom()
    private val projectionMutex = Mutex()

    suspend fun begin(game: String, stake: Long, generatedSeed: String = randomSeed(), clientSeed: String = "local"): ActiveWager? = withContext(NonCancellable) {
        if (game.isBlank() || stake <= 0L) return@withContext null
        wallet.ensureInitialized()
        db.withTransaction {
            val current = requireNotNull(dao.wallet())
            // A double tap must not create two charges and overwrite the first screen state.
            if (current.balance < stake || dao.activeForGame(game) != null) return@withTransaction null
            val record = WagerRecord(UUID.randomUUID().toString(), game, stake, System.currentTimeMillis(), generatedSeed, clientSeed)
            dao.insertWager(WagerRow(record))
            dao.updateWallet(current.copy(balance = current.balance - stake))
            record.active()
        }
    }

    suspend fun increase(wager: ActiveWager, additionalStake: Long): ActiveWager? = withContext(NonCancellable) {
        db.withTransaction {
            val stored = dao.wager(wager.id)?.record ?: return@withTransaction null
            val current = requireNotNull(dao.wallet())
            val change = WagerRules.increase(stored, wager, additionalStake, current.balance) ?: return@withTransaction null
            dao.updateWallet(current.copy(balance = change.balance))
            dao.updateWager(WagerRow(change.record))
            change.record.active()
        }
    }

    /** Save an outcome before revealing/animating it. A terminal outcome cannot be overwritten. */
    suspend fun checkpoint(wager: ActiveWager, multiplier: Double, result: String, details: String = "", terminal: Boolean = true) = withContext(NonCancellable) {
        db.withTransaction {
            val record = requireNotNull(dao.wager(wager.id)?.record) { "Unknown wager" }
            val next = WagerRules.checkpoint(record, wager, multiplier, result, details, terminal)
            if (next != record) dao.updateWager(WagerRow(next))
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun settle(
        wager: ActiveWager,
        multiplier: Double,
        result: String,
        details: String = "",
        generatedSeed: String = "",
        clientSeed: String = "local",
        item: InventoryItemEntity? = null
    ): RoundReceipt = withContext(NonCancellable) {
        val receipt = db.withTransaction {
            val record = requireNotNull(dao.wager(wager.id)?.record) { "Unknown wager" }
            val balance = requireNotNull(dao.wallet()).balance
            val change = WagerRules.settle(record, wager, balance, multiplier, result, details, System.currentTimeMillis())
            if (change.changed) writeChange(change, item)
            change.record.receipt()
        }
        // Progress is a retryable projection, never a reason to refund an already paid round.
        tryFlushProgress()
        receipt
    }

    /** Refund an unused reservation once; if an outcome is saved, settle that outcome instead. */
    suspend fun cancel(wager: ActiveWager) = withContext(NonCancellable) {
        db.withTransaction {
            val record = dao.wager(wager.id)?.record ?: return@withTransaction
            val balance = requireNotNull(dao.wallet()).balance
            val change = WagerRules.cancel(record, wager, balance, System.currentTimeMillis())
            if (change.changed) writeChange(change)
        }
        tryFlushProgress()
    }

    /** Called once before exposing interactive UI after a new application process starts. */
    suspend fun recoverInterruptedRounds(): Int = withContext(NonCancellable) {
        wallet.ensureInitialized()
        val interrupted = dao.activeWagers()
        interrupted.forEach { cancel(it.record.active()) }
        tryFlushProgress()
        interrupted.size
    }

    suspend fun sellInventoryItem(id: String): Long? = withContext(NonCancellable) {
        wallet.ensureInitialized()
        db.withTransaction {
            val item = db.inventoryDao().find(id) ?: return@withTransaction null
            val current = requireNotNull(dao.wallet())
            check(item.marketValue >= 0L)
            if (db.inventoryDao().delete(id) == 0) return@withTransaction null
            dao.updateWallet(current.copy(balance = WagerRules.credit(current.balance, item.marketValue)))
            item.marketValue
        }
    }

    suspend fun resetLocalAccount() = withContext(NonCancellable) {
        wallet.ensureInitialized()
        projectionMutex.withLock {
            db.withTransaction {
                dao.clearWagers()
                dao.updateWallet(WalletRow(balance = WalletRepository.STARTING_BALANCE))
                db.inventoryDao().clear()
                db.gameHistoryDao().clear()
                db.fairnessRoundDao().clear()
            }
            progression.resetRoundProgress()
        }
    }

    suspend fun flushProgress() = projectionMutex.withLock {
        dao.pendingProgress().forEach { row ->
            val record = row.record
            progression.recordSettledRound(record)
            // If the process ends here, recordGame's durable deduplication makes the retry safe.
            dao.acknowledgeProgress(record.id)
        }
    }

    private suspend fun tryFlushProgress() {
        try { flushProgress() }
        catch (error: Exception) { Log.w("GameLedger", "Progress projection will be retried", error) }
    }

    /** Must only be called inside the enclosing Room transaction. */
    private suspend fun writeChange(change: WagerChange, item: InventoryItemEntity? = null) {
        val record = change.record
        dao.updateWallet(WalletRow(balance = change.balance))
        dao.updateWager(WagerRow(record))
        if (record.status != WagerRecord.SETTLED) return
        if (item != null) db.inventoryDao().insert(item)
        db.gameHistoryDao().insert(GameHistoryEntity(
            game = record.game,
            timestamp = record.completedAt,
            stake = record.stake,
            payout = record.payout,
            multiplier = record.multiplier,
            result = record.result,
            details = record.details
        ))
        db.fairnessRoundDao().insert(FairnessRoundEntity(
            roundId = "round-${record.id}",
            game = record.game,
            generatedSeed = record.generatedSeed,
            clientSeed = record.clientSeed,
            result = record.result,
            timestamp = record.completedAt
        ))
    }

    private fun randomSeed(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
