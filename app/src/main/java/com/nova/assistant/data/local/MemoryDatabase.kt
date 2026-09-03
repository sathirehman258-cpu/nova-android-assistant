package com.nova.assistant.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow

enum class MemoryCategory {
    PROFILE, PREFERENCES, ROUTINES, CONTACT_ALIAS, PERSONALITY
}

/**
 * Memory is deliberately flat key/value per category so "forget X" / "forget everything" are
 * simple, auditable operations. Sensitive values (passwords, OTPs, banking credentials, tokens)
 * must never be written here — MemoryManager enforces a blocklist before any insert.
 */
@Entity(tableName = "memory_entries")
data class MemoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: MemoryCategory,
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_entries ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntry>>

    @Query("SELECT * FROM memory_entries WHERE category = :category")
    suspend fun getByCategory(category: MemoryCategory): List<MemoryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MemoryEntry)

    @Query("DELETE FROM memory_entries WHERE category = :category AND key = :key")
    suspend fun deleteByKey(category: MemoryCategory, key: String)

    @Query("DELETE FROM memory_entries")
    suspend fun deleteAll()
}

@Database(entities = [MemoryEntry::class], version = 1, exportSchema = false)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
}
