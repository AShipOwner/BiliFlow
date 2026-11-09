package com.biliflow.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object ImageLoader {
    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtMost(32 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val semaphore = Semaphore(4)
    private lateinit var diskDir: File

    fun init(context: android.content.Context) {
        diskDir = File(context.applicationContext.cacheDir, "bili_images").apply { mkdirs() }
    }

    suspend fun load(url: String, targetWidth: Int): Bitmap? = withContext(Dispatchers.IO) {
        val normalized = Http.normalizeUrl(url)
        if (normalized.isBlank()) return@withContext null

        val width = targetWidth.coerceAtLeast(64)
        val cacheKey = "$normalized#${width / 128}"
        memory.get(cacheKey)?.let { return@withContext it }

        semaphore.withPermit {
            memory.get(cacheKey)?.let { return@withPermit it }

            val localPath = when {
                normalized.startsWith("file://") -> normalized.removePrefix("file://")
                normalized.startsWith("/") -> normalized
                else -> null
            }
            val bytes = if (localPath != null) {
                runCatching { File(localPath).readBytes() }.getOrNull() ?: return@withPermit null
            } else {
                val file = if (::diskDir.isInitialized) File(diskDir, md5(normalized)) else null
                val cachedBytes = file?.takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }
                cachedBytes ?: runCatching {
                    Http.getBytes(normalized).also { data ->
                        if (file != null) writeCache(file, data)
                    }
                }.getOrNull() ?: return@withPermit null
            }

            val bitmap = decode(bytes, width) ?: return@withPermit null
            memory.put(cacheKey, bitmap)
            bitmap
        }
    }

    suspend fun cacheSize(): Long = withContext(Dispatchers.IO) {
        if (!::diskDir.isInitialized) return@withContext 0L
        diskDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun clear() {
        memory.evictAll()
        if (::diskDir.isInitialized) {
            diskDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun writeCache(file: File, bytes: ByteArray) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            pruneIfNeeded()
        }
    }

    private fun pruneIfNeeded() {
        val files = diskDir.listFiles() ?: return
        val total = files.sumOf { it.length() }
        if (total <= 80L * 1024 * 1024) return
        files.sortedBy { it.lastModified() }.take(40).forEach { it.delete() }
    }

    private fun decode(bytes: ByteArray, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
