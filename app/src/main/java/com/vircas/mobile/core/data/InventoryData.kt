package com.vircas.mobile.core.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "inventory")
data class InventoryItemEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val name: String,
    val weaponCategory: String,
    val rarity: String,
    val marketValue: Long,
    val previewKey: String,
    val acquiredAt: Long
)

@Dao
interface InventoryDao {
    @Insert suspend fun insert(item: InventoryItemEntity)
    @Query("SELECT * FROM inventory ORDER BY acquiredAt DESC")
    fun observeAll(): Flow<List<InventoryItemEntity>>
    @Query("SELECT * FROM inventory WHERE id = :id LIMIT 1")
    suspend fun find(id: String): InventoryItemEntity?
    @Query("DELETE FROM inventory WHERE id = :id")
    suspend fun delete(id: String): Int
    @Query("DELETE FROM inventory")
    suspend fun clear()
}

class InventoryRepository(private val dao: InventoryDao) {
    val items: Flow<List<InventoryItemEntity>> = dao.observeAll()
    suspend fun add(item: InventoryItemEntity) = dao.insert(item)
    suspend fun find(id: String) = dao.find(id)
    suspend fun remove(id: String): Boolean = dao.delete(id) > 0
    suspend fun clear() = dao.clear()
}
