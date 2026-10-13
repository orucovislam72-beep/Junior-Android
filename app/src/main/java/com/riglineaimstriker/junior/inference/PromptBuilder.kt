package com.riglineaimstriker.junior.inference

/** ChatML — формат Qwen2.5. Старые реплики отбрасываются, чтобы влезть в контекст. */
object PromptBuilder {
    private const val SYSTEM =
        "You are Junior, a local AI assistant running fully on the user's own device, " +
            "with no cloud and no API key. Be concise and practical. " +
            "Answer in the language the user writes in. Do not claim to be any other product."

    // Для кириллицы токенайзер Qwen даёт ~2 символа на токен — берём с запасом.
    private const val CHARS_PER_TOKEN = 2

    fun build(history: List<Pair<Boolean, String>>, budgetTokens: Int): String {
        val budget = budgetTokens * CHARS_PER_TOKEN
        var used = SYSTEM.length
        val kept = ArrayDeque<Pair<Boolean, String>>()
        for ((isUser, text) in history.asReversed()) {
            val room = budget - used - 24
            if (room <= 0) break
            val t = if (text.length > room) {
                if (kept.isNotEmpty()) break else text.takeLast(room)
            } else text
            kept.addFirst(isUser to t)
            used += t.length + 24
        }
        return buildString {
            append("<|im_start|>system\n").append(SYSTEM).append("<|im_end|>\n")
            for ((isUser, t) in kept) {
                append("<|im_start|>").append(if (isUser) "user" else "assistant").append('\n')
                append(t).append("<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }
    }
}
