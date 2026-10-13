package com.riglineaimstriker.junior.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Running(val done: Long, val total: Long) : DownloadState
    data class Failed(val message: String) : DownloadState
    data object Done : DownloadState
}

@Singleton
class ModelRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
    val modelsDir: File = File(ctx.filesDir, "models").apply { mkdirs() }
    private val tick = MutableStateFlow(0)
    private val work get() = WorkManager.getInstance(ctx)

    fun file(p: ModelProfile) = File(modelsDir, p.fileName)

    fun looksLikeGguf(f: File): Boolean = runCatching {
        f.inputStream().use { s ->
            val h = ByteArray(4)
            s.read(h) == 4 && String(h, Charsets.US_ASCII) == "GGUF"
        }
    }.getOrDefault(false)

    fun isInstalled(p: ModelProfile): Boolean {
        val f = file(p)
        return f.isFile && f.length() > 0 && looksLikeGguf(f)
    }

    fun downloadState(p: ModelProfile): Flow<DownloadState> =
        combine(work.getWorkInfosForUniqueWorkFlow(p.id), tick) { infos, _ ->
            val info = infos.firstOrNull()
            when {
                isInstalled(p) -> DownloadState.Done
                info == null -> DownloadState.Idle
                else -> when (info.state) {
                    WorkInfo.State.RUNNING -> DownloadState.Running(
                        info.progress.getLong(DownloadWorker.KEY_DONE, 0),
                        info.progress.getLong(DownloadWorker.KEY_TOTAL, 0),
                    )
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadState.Running(0, 0)
                    WorkInfo.State.FAILED -> DownloadState.Failed(
                        info.outputData.getString(DownloadWorker.KEY_ERROR) ?: "Не удалось скачать модель"
                    )
                    WorkInfo.State.SUCCEEDED -> DownloadState.Done
                    WorkInfo.State.CANCELLED -> DownloadState.Idle
                }
            }
        }

    fun startDownload(p: ModelProfile) {
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_ID to p.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniqueWork(p.id, ExistingWorkPolicy.KEEP, req)
    }

    fun cancel(p: ModelProfile) {
        work.cancelUniqueWork(p.id)
    }

    fun delete(p: ModelProfile) {
        work.cancelUniqueWork(p.id)
        file(p).delete()
        File(file(p).path + ".part").delete()
        tick.update { it + 1 }
    }

    /** Есть ли модель, вшитая в APK (assets/models/<bundledAsset>). */
    fun hasBundled(p: ModelProfile): Boolean {
        val name = p.bundledAsset ?: return false
        return runCatching { ctx.assets.list("models")?.contains(name) == true }.getOrDefault(false)
    }

    /** Копирует вшитую модель из APK во внутреннюю память (один раз). */
    suspend fun installBundled(p: ModelProfile, onProgress: (done: Long, total: Long) -> Unit) =
        withContext(Dispatchers.IO) {
            val name = p.bundledAsset ?: error("Модель не вшита в приложение")
            val assetPath = "models/$name"
            val total = runCatching { ctx.assets.openFd(assetPath).use { it.length } }.getOrDefault(-1L)
            checkSpace(total)
            val part = File(file(p).path + ".part")
            part.delete()
            ctx.assets.open(assetPath, android.content.res.AssetManager.ACCESS_STREAMING).use { input ->
                copyTo(input, part, total, onProgress)
            }
            finishInstall(part, p, "Файл модели в приложении повреждён")
        }

    /** Копирует выбранный пользователем .gguf файл во внутреннюю память. */
    suspend fun importFromUri(uri: Uri, p: ModelProfile, onProgress: (done: Long, total: Long) -> Unit) =
        withContext(Dispatchers.IO) {
            val total = querySize(uri)
            checkSpace(total)
            val part = File(file(p).path + ".part")
            part.delete()
            val input = ctx.contentResolver.openInputStream(uri) ?: error("Не удалось открыть выбранный файл")
            input.use { copyTo(it, part, total, onProgress) }
            finishInstall(part, p, "Это не файл модели GGUF. Выберите файл с расширением .gguf")
        }

    private fun querySize(uri: Uri): Long = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    private fun checkSpace(total: Long) {
        if (total > 0 && modelsDir.usableSpace < total + EXTRA_SPACE) {
            val needGb = String.format(java.util.Locale.US, "%.1f", (total + EXTRA_SPACE) / 1e9)
            error("Недостаточно свободного места: нужно около $needGb ГБ. Освободите память и повторите.")
        }
    }

    private suspend fun copyTo(
        input: InputStream, part: File, total: Long, onProgress: (done: Long, total: Long) -> Unit,
    ) {
        part.outputStream().use { output ->
            val buf = ByteArray(1 shl 20)
            var done = 0L
            var lastReport = 0L
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                output.write(buf, 0, n)
                done += n
                if (done - lastReport >= REPORT_STEP) {
                    lastReport = done
                    onProgress(done, total)
                }
            }
            output.fd.sync()
            onProgress(done, total)
        }
    }

    private fun finishInstall(part: File, p: ModelProfile, badMessage: String) {
        if (!looksLikeGguf(part)) {
            part.delete()
            error(badMessage)
        }
        val dst = file(p)
        dst.delete()
        if (!part.renameTo(dst)) {
            part.delete()
            error("Не удалось сохранить модель")
        }
        tick.update { it + 1 }
    }

    private companion object {
        const val EXTRA_SPACE = 256L * 1024 * 1024
        const val REPORT_STEP = 8L * 1024 * 1024
    }
}
