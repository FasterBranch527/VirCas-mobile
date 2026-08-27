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
    val result: String
) {
    val profitLoss: Long get() = payout - stake
}

@Dao
interface GameHistoryDao {
    @Insert suspend fun insert(item: GameHistoryEntity)
    @Query("SELECT * FROM game_history ORDER BY timestamp DESC LIMIT :limit")
    fun recent(limit: Int = 30): Flow<List<GameHistoryEntity>>
    @Query("DELETE FROM game_history") suspend fun clear()
}

@Database(entities = [GameHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameHistoryDao(): GameHistoryDao
}

class GameHistoryRepository(private val dao: GameHistoryDao) {
    fun recent(limit: Int = 30) = dao.recent(limit)
    suspend fun record(game: String, stake: Long, payout: Long, multiplier: Double, result: String) =
        dao.insert(GameHistoryEntity(game = game, timestamp = System.currentTimeMillis(), stake = stake, payout = payout, multiplier = multiplier, result = result))
}
