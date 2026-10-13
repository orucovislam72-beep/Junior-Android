package com.riglineaimstriker.junior.inference

import com.riglineaimstriker.junior.model.AppPrefs
import com.riglineaimstriker.junior.model.DeviceTuner
import com.riglineaimstriker.junior.model.ModelRepository
import com.riglineaimstriker.junior.model.Profiles
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Общая для чата и агента загрузка модели и подгонка промпта под контекст.
 * Движок один на всё приложение, поэтому и параметры (размер контекста) живут здесь.
 */
@Singleton
class ModelSession @Inject constructor(
    val engine: InferenceEngine,
    private val repo: ModelRepository,
    private val prefs: AppPrefs,
    private val tuner: DeviceTuner,
) {
    @Volatile var ctxSize: Int = 2048
        private set

    /** Загрузить выбранную модель (повторный вызов с той же моделью ничего не делает). */
    suspend fun load(id: String? = prefs.selected.value) {
        val p = Profiles.byId(id) ?: return
        if (!repo.isInstalled(p)) {
            engine.reportFailure("Файл модели не найден. Скачайте её заново.")
            return
        }
        ctxSize = tuner.contextFor(p)
        engine.load(repo.file(p).path, ctxSize, tuner.threads())
    }

    /** Сколько токенов отдать под ответ при текущих настройках. */
    fun maxNewTokens(): Int =
        prefs.maxTokens.value.coerceIn(128, (ctxSize - 512).coerceAtLeast(128))

    /** Примерный бюджет (в символах) для вложенного файла, чтобы он влез в контекст целиком. */
    fun attachmentBudgetChars(): Int =
        ((ctxSize - maxNewTokens() - 900).coerceAtLeast(300) * 3)

    /**
     * Собирает промпт, который гарантированно влезает в [limitTokens] токенов:
     * сначала по грубой оценке (символы), затем проверяет точным подсчётом токенов
     * и при необходимости ужимает историю.
     */
    fun fit(build: (budgetChars: Int) -> String, limitTokens: Int): String {
        var budget = limitTokens * 3
        var prompt = build(budget)
        repeat(6) {
            val n = engine.countTokens(prompt)
            if (n < 0 || n <= limitTokens) return prompt
            budget = (budget.toLong() * limitTokens / n * 9 / 10).toInt().coerceAtLeast(200)
            prompt = build(budget)
        }
        return prompt
    }
}
