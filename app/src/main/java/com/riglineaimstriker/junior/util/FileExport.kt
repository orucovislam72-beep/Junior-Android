package com.riglineaimstriker.junior.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Отправка текста как файла через системное меню «Поделиться» (Telegram, Файлы, почта и т.д.). */
object FileExport {

    fun extFor(lang: String): String = when (lang.trim().lowercase()) {
        "kotlin", "kt" -> "kt"
        "python", "py" -> "py"
        "javascript", "js" -> "js"
        "typescript", "ts" -> "ts"
        "java" -> "java"
        "html" -> "html"
        "css" -> "css"
        "json" -> "json"
        "bash", "sh", "shell" -> "sh"
        "c" -> "c"
        "cpp", "c++" -> "cpp"
        "xml" -> "xml"
        "yaml", "yml" -> "yml"
        "sql" -> "sql"
        "go" -> "go"
        "rust", "rs" -> "rs"
        "markdown", "md" -> "md"
        else -> "txt"
    }

    private fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "")) {
        "html" -> "text/html"
        "json" -> "application/json"
        "xml" -> "text/xml"
        else -> "text/plain"
    }

    fun share(ctx: Context, fileName: String, text: String) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val dayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        dir.listFiles()?.forEach { if (it.lastModified() < dayAgo) it.delete() }

        val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val f = File(dir, safe)
        f.writeText(text)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeFor(safe)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("file", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Отправить файл").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }
}
