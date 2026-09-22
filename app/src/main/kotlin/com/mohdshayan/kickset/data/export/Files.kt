package com.mohdshayan.kickset.data.export

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reads and writes documents the user picked through the system file picker. No storage permission. */
object Files {
    suspend fun write(context: Context, uri: Uri, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() } ?: return@withContext false
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Why a read produced no bytes. "Too big" and "unreadable" need different sentences. */
    sealed interface Read {
        data class Bytes(val bytes: ByteArray) : Read
        data object TooLarge : Read
        data object Failed : Read
    }

    /** Reads at most `max` bytes, and says which of the two failures happened. */
    suspend fun read(context: Context, uri: Uri, max: Int): Read = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > max) return@withContext Read.TooLarge
                    out.write(buf, 0, n)
                }
                Read.Bytes(out.toByteArray())
            } ?: Read.Failed
        } catch (e: Exception) {
            Read.Failed
        }
    }

    fun isoDate(now: Long = System.currentTimeMillis()): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(now))

    fun readableDate(at: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(at))

    fun safeStem(name: String): String = name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "job" }
}
