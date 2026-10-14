package com.riglineaimstriker.junior.inference

/**
 * ChatML — формат Qwen2.5. Старые реплики отбрасываются, чтобы влезть в контекст.
 *
 * Системный промпт (база + навыки) стоит первым и не меняется от реплики к реплике,
 * поэтому нативный KV-кэш переиспользует его и всю прошлую переписку.
 */
object PromptBuilder {
    private const val OVERHEAD = 24

    /**
     * @param history пары (это пользователь?, текст), последняя — текущий запрос.
     * @param budgetChars примерный бюджет в символах на всё, включая системный промпт.
     * @param assistantPrefix начало ответа ассистента (обычно пусто).
     * @param keepFirst всегда сохранять первую реплику (задачу агента), даже если старое выкидывается.
     */
    fun build(
        system: String,
        history: List<Pair<Boolean, String>>,
        budgetChars: Int,
        assistantPrefix: String = "",
        keepFirst: Boolean = false,
    ): String {
        var used = system.length + OVERHEAD
        var pinned: Pair<Boolean, String>? = null
        var rest = history
        if (keepFirst && history.size > 1) {
            val first = history.first()
            val t = cutMiddle(first.second, (budgetChars / 3).coerceAtLeast(200))
            pinned = first.first to t
            used += t.length + OVERHEAD
            rest = history.drop(1)
        }
        val kept = ArrayDeque<Pair<Boolean, String>>()
        for ((isUser, text) in rest.asReversed()) {
            val room = budgetChars - used - OVERHEAD
            if (room <= 0) break
            val t = if (text.length > room) {
                if (kept.isNotEmpty()) break else cutMiddle(text, room)
            } else text
            kept.addFirst(isUser to t)
            used += t.length + OVERHEAD
        }
        return buildString {
            append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
            val all = listOfNotNull(pinned) + kept
            for ((isUser, t) in all) {
                append("<|im_start|>").append(if (isUser) "user" else "assistant").append('\n')
                append(t).append("<|im_end|>\n")
            }
            append("<|im_start|>assistant\n").append(assistantPrefix)
        }
    }

    /** Обрезает середину: сохраняет начало (файл/контекст) и конец (сам вопрос). */
    fun cutMiddle(text: String, max: Int): String {
        if (text.length <= max) return text
        val marker = "\n…[обрезано]…\n"
        val keep = (max - marker.length).coerceAtLeast(0)
        val head = keep * 3 / 5
        val tail = keep - head
        return text.take(head) + marker + text.takeLast(tail)
    }
}
