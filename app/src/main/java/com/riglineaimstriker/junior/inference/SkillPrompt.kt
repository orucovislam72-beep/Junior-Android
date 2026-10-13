package com.riglineaimstriker.junior.inference

/** Подмешивает инструкции навыков в последнее сообщение пользователя (не зависит от шаблона модели). */
object SkillPrompt {
    fun apply(history: List<Pair<Boolean, String>>, rules: String): List<Pair<Boolean, String>> {
        if (rules.isBlank()) return history
        val idx = history.indexOfLast { it.first }
        if (idx < 0) return history
        val (isUser, text) = history[idx]
        val merged = isUser to "$rules\n\n---\nЗапрос пользователя:\n$text"
        return history.toMutableList().also { it[idx] = merged }
    }
}
