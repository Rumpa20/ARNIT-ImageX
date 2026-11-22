package com.arnit.imagex.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.arnit.imagex.core.ConversionException
import com.arnit.imagex.core.ErrorCode
import java.io.File
import java.io.IOException

/**
 * All Storage Access Framework interactions: reading sources (no storage
 * permission needed) and writing the user's Save destinations.
 */
object DocumentStore {

    data class PickedFile(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val mime: String?
    )

    fun probe(context: Context, uri: Uri, mimeHint: String? = null): PickedFile {
        val resolver = context.contentResolver
        var name = "image"
        var size = -1L
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0) ?: name
                        size = if (cursor.isNull(1)) -1L else cursor.getLong(1)
                    }
                }
        } catch (_: Exception) {
        }
        val mime = try {
            resolver.getType(uri) ?: mimeHint
        } catch (_: Exception) {
            mimeHint
        }
        if (name == "image" || !name.contains('.')) {
            name = nameIfMissingExtension(uri, name)
        }
        return PickedFile(uri, name, size, mime)
    }

    private fun nameIfMissingExtension(uri: Uri, fallback: String): String {
        val last = uri.lastPathSegment ?: return fallback
        val guess = last.substringAfterLast('/')
        return if (guess.contains('.')) guess else fallback
    }

    fun readBytes(context: Context, uri: Uri): ByteArray =
        try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw ConversionException(ErrorCode.IO_ERROR, "The file could not be opened.")
        } catch (e: ConversionException) {
            throw e
        } catch (e: SecurityException) {
            throw ConversionException(ErrorCode.IO_ERROR, "Access to the file was revoked.", e.message, e)
        } catch (e: IOException) {
            throw ConversionException(ErrorCode.IO_ERROR, "Reading the file failed.", e.message, e)
        }

    /** Writes the finished image to an SAF destination chosen by the user. */
    fun saveTo(context: Context, source: File, destination: Uri): Long {
        if (!source.exists()) {
            throw ConversionException(ErrorCode.IO_ERROR, "The converted image is gone; please convert again.")
        }
        val resolver = context.contentResolver
        val writtenBytes = source.length()
        try {
            resolver.openOutputStream(destination, "wt")?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
                out.flush()
            } ?: throw ConversionException(ErrorCode.IO_ERROR, "The destination could not be opened.")
            source.delete() // the destination copy is user-owned; the scratch copy is no longer needed
            return writtenBytes
        } catch (e: ConversionException) {
            throw e
        } catch (e: IOException) {
            val message = if (e.message?.contains("ENOSPC") == true) {
                "Not enough free storage at the chosen destination."
            } else {
                "Saving to the selected destination failed."
            }
            throw ConversionException(ErrorCode.INSUFFICIENT_STORAGE, message, e.message, e)
        }
    }

    /** Batch save-all into a user-chosen tree. */
    fun saveAllToTree(context: Context, treeUri: Uri, files: List<Pair<File, String>>): List<File> {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw ConversionException(ErrorCode.IO_ERROR, "The destination folder could not be opened.")
        val failures = mutableListOf<File>()
        for ((file, displayName) in files) {
            try {
                val target = tree.createFile(guessMime(displayName), displayName)
                    ?: throw IOException("createFile failed")
                context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: throw IOException("openOutputStream failed")
                file.delete()
            } catch (e: Exception) {
                failures.add(file)
            }
        }
        return failures
    }

    fun guessMime(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "avif" -> "image/avif"
            "heic", "heif" -> "image/heif"
            "bmp" -> "image/bmp"
            "gif" -> "image/gif"
            "tiff", "tif" -> "image/tiff"
            "tga" -> "image/x-tga"
            "ico" -> "image/vnd.microsoft.icon"
            "wbmp" -> "image/vnd.wap.wbmp"
            "ppm", "pgm", "pbm" -> "image/x-portable-anymap"
            else -> "application/octet-stream"
        }
    }
}
