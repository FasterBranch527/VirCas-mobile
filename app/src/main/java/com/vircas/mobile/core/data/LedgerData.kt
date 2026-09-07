package com.vircas.mobile.core.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vircas.mobile.core.game.WagerRecord
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "wallet_state")
data class WalletRow(@PrimaryKey val id: Int = 0, val balance: Long)

@Entity(tableName = "game_wagers", primaryKeys = ["id"], indices = [Index(value = ["status", "game"])])
data class WagerRow(@Embedded val record: WagerRecord)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM wallet_state WHERE id = 0") suspend fun wallet(): WalletRow?
    @Query("SELECT balance FROM wallet_state WHERE id = 0") fun observeBalance(): Flow<Long?>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun initializeWallet(row: WalletRow)
    @Update suspend fun updateWallet(row: WalletRow)
    @Insert suspend fun insertWager(row: WagerRow)
    @Update suspend fun updateWager(row: WagerRow)
    @Query("SELECT * FROM game_wagers WHERE id = :id") suspend fun wager(id: String): WagerRow?
    @Query("SELECT * FROM game_wagers WHERE status = 'ACTIVE' AND game = :game LIMIT 1") suspend fun activeForGame(game: String): WagerRow?
    @Query("SELECT * FROM game_wagers WHERE status = 'ACTIVE' ORDER BY startedAt, id") suspend fun activeWagers(): List<WagerRow>
    @Query("SELECT * FROM game_wagers WHERE status = 'SETTLED' AND progressSynced = 0 ORDER BY completedAt, id") suspend fun pendingProgress(): List<WagerRow>
    @Query("UPDATE game_wagers SET progressSynced = 1 WHERE id = :id AND status = 'SETTLED'") suspend fun acknowledgeProgress(id: String)
    @Query("DELETE FROM game_wagers") suspend fun clearWagers()
}

/** Additive migration: no existing history, inventory or fairness tables are dropped. */
val LEDGER_MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS wallet_state (id INTEGER NOT NULL, balance INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS game_wagers (
                id TEXT NOT NULL, game TEXT NOT NULL, stake INTEGER NOT NULL,
                startedAt INTEGER NOT NULL, generatedSeed TEXT NOT NULL, clientSeed TEXT NOT NULL,
                revision INTEGER NOT NULL, status TEXT NOT NULL,
                checkpointMultiplier REAL, checkpointResult TEXT, checkpointDetails TEXT NOT NULL,
                checkpointTerminal INTEGER NOT NULL, payout INTEGER NOT NULL, multiplier REAL NOT NULL,
                result TEXT NOT NULL, details TEXT NOT NULL, completedAt INTEGER NOT NULL,
                progressSynced INTEGER NOT NULL, PRIMARY KEY(id)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_game_wagers_status_game ON game_wagers (status, game)")
    }
}
