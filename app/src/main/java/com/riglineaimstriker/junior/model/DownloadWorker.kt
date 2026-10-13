package com.riglineaimstriker.junior.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Скачивание GGUF с докачкой (Range). Переживает сворачивание приложения. */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repo: ModelRepository,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val profile = Profiles.byId(inputData.getString(KEY_ID))
            ?: return fail("Неизвестная модель")
        val target = repo.file(profile)
        val part = File(target.path + ".part")
        val have = if (part.exists()) part.length() else 0L
        val needed = (profile.sizeGb * 1e9).toLong() + 300_000_000L
        if (repo.modelsDir.usableSpace + have < needed) return fail("Недостаточно места на устройстве")

        runCatching { setForeground(foregroundInfo(profile, 0)) }
        return try {
            withContext(Dispatchers.IO) { download(profile, part) }
            if (!repo.looksLikeGguf(part)) {
                part.delete()
                fail("Файл модели повреждён. Попробуйте ещё раз")
            } else {
                target.delete()
                if (part.renameTo(target)) Result.success() else fail("Не удалось сохранить файл модели")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (runAttemptCount < 5) Result.retry()
            else fail("Нет соединения. Проверьте интернет и повторите")
        }
    }

    private suspend fun download(profile: ModelProfile, part: File) {
        var offset = if (part.exists()) part.length() else 0L
        val conn = (URL(profile.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            val code = conn.responseCode
            if (code == 416) return
            if (code == 200 && offset > 0) { part.delete(); offset = 0 }
            if (code != 200 && code != 206) throw IOException("HTTP $code")

            val len = conn.contentLengthLong
            val total = if (len > 0) offset + len else (profile.sizeGb * 1e9).toLong()
            var done = offset
            var lastReport = 0L
            conn.inputStream.use { input ->
                FileOutputStream(part, offset > 0).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastReport > 700) {
                            lastReport = now
                            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
                            runCatching { setForeground(foregroundInfo(profile, (done * 100 / total).toInt())) }
                        }
                    }
                }
            }
            if (len > 0 && done < total) throw IOException("Обрыв загрузки")
        } finally {
            conn.disconnect()
        }
    }

    private fun fail(msg: String) = Result.failure(workDataOf(KEY_ERROR to msg))

    private fun foregroundInfo(p: ModelProfile, percent: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Загрузка модели", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Загрузка ${p.name}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, percent == 0)
            .build()
        val id = p.id.hashCode()
        return if (Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id, n)
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val CHANNEL = "model_download"
    }
}
