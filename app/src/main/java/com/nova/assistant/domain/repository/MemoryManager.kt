package com.nova.assistant.domain.repository

import com.nova.assistant.data.local.MemoryCategory
import com.nova.assistant.data.local.MemoryDao
import com.nova.assistant.data.local.MemoryEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryManager @Inject constructor(
    private val dao: MemoryDao
) {
    /**
     * Never store credentials/secrets, regardless of what the user says to save. This is a
     * hard blocklist independent of any AI classification, checked before every write.
     */
    private val blockedKeyHints = listOf(
        "password", "otp", "pin", "bank", "card number", "cvv", "private key", "token", "secret"
    )

    fun observeAll(): Flow<List<MemoryEntry>> = dao.observeAll()

    suspend fun remember(category: MemoryCategory, key: String, value: String): Boolean {
        val lowerKey = key.lowercase()
        val lowerVal = value.lowercase()
        if (blockedKeyHints.any { lowerKey.contains(it) || lowerVal.contains(it) }) {
            return false
        }
        dao.upsert(MemoryEntry(category = category, key = key, value = value))
        return true
    }

    suspend fun forget(category: MemoryCategory, key: String) {
        dao.deleteByKey(category, key)
    }

    suspend fun forgetEverything() {
        dao.deleteAll()
    }

    suspend fun profileFor(category: MemoryCategory): List<MemoryEntry> = dao.getByCategory(category)
}
