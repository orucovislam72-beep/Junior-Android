package com.riglineaimstriker.junior.model

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Подбор модели и параметров под устройство (по мотивам device.py). */
@Singleton
class DeviceTuner @Inject constructor(@ApplicationContext private val ctx: Context) {

    fun totalRamGb(): Double {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.totalMem / 1024.0 / 1024.0 / 1024.0
    }

    fun canRun(p: ModelProfile) = totalRamGb() >= p.minRamGb

    fun recommended(): ModelProfile =
        if (totalRamGb() >= 10.0) Profiles.Pro else Profiles.Mini

    fun threads(): Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)

    fun contextFor(p: ModelProfile): Int =
        minOf(p.recommendedCtx, if (totalRamGb() < 6.5) 2048 else 4096)
}
