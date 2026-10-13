package com.riglineaimstriker.junior.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Чтение небольших текстовых файлов через Storage Access Framework. */
object TextFiles {
    const val MAX_ATTACH_BYTES = 64 * 1024

    class Picked(val name: String, val text: String, val sizeBytes: Long)

    /** Имя файла по Uri (или запасное). */
    fun displayName(ctx: Context, uri: Uri, fallback: String = "file.txt"): String = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: fallback

    /**
     * Читает текстовый файл не больше [maxBytes]. Бросает IllegalArgumentException
     * с понятным текстом, если файл слишком большой или бинарный.
     */
    fun read(ctx: Context, uri: Uri, maxBytes: Int = MAX_ATTACH_BYTES): Picked {
        val name = displayName(ctx, uri)
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { input ->
            val buf = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0
            while (true) {
                val k = input.read(chunk)
                if (k < 0) break
                total += k
                require(total <= maxBytes) { "Файл больше ${maxBytes / 1024} КБ — приложите файл поменьше." }
                buf.write(chunk, 0, k)
            }
            buf.toByteArray()
        } ?: throw IllegalArgumentException("Не удалось открыть файл.")
        require(!looksBinary(bytes)) { "Это не текстовый файл. Приложите файл с кодом или текстом." }
        val text = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        return Picked(name, text, bytes.size.toLong())
    }

    fun looksBinary(bytes: ByteArray): Boolean {
        val n = minOf(bytes.size, 4096)
        for (i in 0 until n) if (bytes[i].toInt() == 0) return true
        return false
    }
}
