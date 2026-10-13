package com.riglineaimstriker.junior.model

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.io.File
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
}
