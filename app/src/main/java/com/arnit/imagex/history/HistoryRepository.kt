package com.arnit.imagex.history

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Metadata-only conversion history. Image contents are never stored here -
 * only names, formats, sizes, timestamps and status. Written as JSON in
 * app-private storage; anything persistent stays out of the way of privacy
 * by the opt-out setting.
 */
@Serializable
data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val inputFormat: String,
    val outputFormat: String,
    val timestampMs: Long,
    val originalSizeBytes: Long,
    val convertedSizeBytes: Long,
    val status: String,
    val width: Int = 0,
    val height: Int = 0
)

class HistoryRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())

    val entries: StateFlow<List<HistoryEntry>> = _entries

    private val file: File get() = File(context.filesDir, "conversion_history.json")

    init {
        reload()
    }

    fun reload() {
        try {
            if (file.exists()) {
                val list = json.decodeFromString<List<HistoryEntry>>(file.readText())
                _entries.value = list
            } else {
                _entries.value = emptyList()
            }
        } catch (_: Exception) {
            _entries.value = emptyList()
        }
    }

    suspend fun record(entry: HistoryEntry) {
        mutex.withLock {
            val updated = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
            _entries.value = updated
            persistLocked(updated)
        }
    }

    suspend fun delete(entryId: String) {
        mutex.withLock {
            val updated = _entries.value.filterNot { it.id == entryId }
            _entries.value = updated
            persistLocked(updated)
        }
    }

    suspend fun clearAll() {
        mutex.withLock {
            _entries.value = emptyList()
            persistLocked(emptyList())
        }
    }

    private fun persistLocked(list: List<HistoryEntry>) {
        try {
            if (list.isEmpty()) {
                if (file.exists()) file.delete()
            } else {
                file.writeText(json.encodeToString(list))
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val MAX_ENTRIES = 200
    }
}
