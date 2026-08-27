package com.vircas.mobile.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "game_history")
data class GameHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val game: String,
    val timestamp: Long,
    val stake: Long,
    val payout: Long,
    val multiplier: Double,
    val result: String,
    val details: String = ""
) {
    val profitLoss: Long get() = payout - stake
    val won: Boolean get() = payout > stake
}

@Entity(tableName = "fairness_rounds")
data class FairnessRoundEntity(
    @PrimaryKey val roundId: String,
    val game: String,
    val generatedSeed: String,
    val clientSeed: String,
    val result: String,
    val timestamp: Long
)

@Dao
interface GameHistoryDao {
    @Insert suspend fun insert(item: GameHistoryEntity)

    @Query("SELECT * FROM game_history ORDER BY timestamp DESC LIMIT :limit")
    fun recent(limit: Int = 30): Flow<List<GameHistoryEntity>>

    @Query("SELECT * FROM game_history WHERE game = :game ORDER BY timestamp DESC LIMIT :limit")
    fun byGame(game: String, limit: Int = 100): Flow<List<GameHistoryEntity>>

    @Query("SELECT * FROM game_history WHERE payout > stake ORDER BY timestamp DESC LIMIT :limit")
    fun wins(limit: Int = 100): Flow<List<GameHistoryEntity>>

    @Query("SELECT * FROM game_history WHERE payout <= stake ORDER BY timestamp DESC LIMIT :limit")
    fun losses(limit: Int = 100): Flow<List<GameHistoryEntity>>

    @Query("DELETE FROM game_history")
    suspend fun clear()
}

@Dao
interface FairnessRoundDao {
    @Insert suspend fun insert(item: FairnessRoundEntity)

    @Query("SELECT * FROM fairness_rounds ORDER BY timestamp DESC LIMIT :limit")
    fun recent(limit: Int = 100): Flow<List<FairnessRoundEntity>>

    @Query("DELETE FROM fairness_rounds")
    suspend fun clear()
}

@Database(
    entities = [GameHistoryEntity::class, InventoryItemEntity::class, FairnessRoundEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameHistoryDao(): GameHistoryDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun fairnessRoundDao(): FairnessRoundDao
}

class GameHistoryRepository(private val dao: GameHistoryDao) {
    fun recent(limit: Int = 30) = dao.recent(limit)
    fun wins(limit: Int = 100) = dao.wins(limit)
    fun losses(limit: Int = 100) = dao.losses(limit)
    fun byGame(game: String, limit: Int = 100) = dao.byGame(game, limit)

    suspend fun record(
        game: String,
        stake: Long,
        payout: Long,
        multiplier: Double,
        result: String,
        details: String = ""
    ) = dao.insert(
        GameHistoryEntity(
            game = game,
            timestamp = System.currentTimeMillis(),
            stake = stake,
            payout = payout,
            multiplier = multiplier,
            result = result,
            details = details
        )
    )
}

class FairnessRepository(private val dao: FairnessRoundDao) {
    fun recent(limit: Int = 100) = dao.recent(limit)

    suspend fun record(game: String, generatedSeed: String, clientSeed: String, result: String): String {
        val now = System.currentTimeMillis()
        val roundId = "${game.lowercase().replace(' ', '_')}-${now.toString(36)}-${generatedSeed.take(6)}"
        dao.insert(
            FairnessRoundEntity(
                roundId = roundId,
                game = game,
                generatedSeed = generatedSeed,
                clientSeed = clientSeed,
                result = result,
                timestamp = now
            )
        )
        return roundId
    }
}
