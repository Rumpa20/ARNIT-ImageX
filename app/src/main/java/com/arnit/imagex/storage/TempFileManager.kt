package com.arnit.imagex.storage

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the private conversion scratch space under app cache storage.
 * Rules implemented here:
 *  - every conversion writes only inside this directory
 *  - files are removed on success-after-save, on cancel and on failure
 *  - startup cleanup deletes anything left over
 *  - periodic cleanup removes files older than the TTL
 *  - no image data ever reaches a database or log
 */
class TempFileManager(private val context: Context) {

    private val timeStamps = ConcurrentHashMap<File, Long>()

    val dir: File
        get() {
            val d = File(context.cacheDir, "imagex_tmp")
            if (!d.exists()) d.mkdirs()
            return d
        }

    fun newTempFile(extension: String): File {
        val f = File(dir, "arnit_${java.util.UUID.randomUUID()}.$extension")
        timeStamps[f] = System.currentTimeMillis()
        return f
    }

    fun delete(file: File?) {
        if (file == null) return
        try {
            if (file.exists()) file.delete()
        } catch (_: Exception) {
        } finally {
            timeStamps.remove(file)
        }
    }

    fun cleanupAll() {
        dir.listFiles()?.forEach { file ->
            try {
                file.delete()
            } catch (_: Exception) {
            }
        }
        timeStamps.clear()
    }

    /** Removes files older than [ttlMs] (share leftovers etc.). */
    fun purgeOlderThan(ttlMs: Long = DEFAULT_TTL_MS) {
        val cutoff = System.currentTimeMillis() - ttlMs
        dir.listFiles()?.forEach { file ->
            try {
                if (file.lastModified() < cutoff) file.delete()
            } catch (_: Exception) {
            }
        }
        timeStamps.entries.removeIf { it.value < cutoff }
    }

    companion object {
        const val DEFAULT_TTL_MS = 60L * 60 * 1000
    }
}
