package com.riglineaimstriker.junior.ui.chat

/** Файл с кодом, приложенный к сообщению. */
data class Attachment(
    val name: String,
    val lang: String,
    val content: String,
    /** Исходный размер в байтах (для подписи на чипе). */
    val sizeBytes: Long,
)

/** Скорость и заполненность контекста для ответа. */
data class GenStats(
    val tokens: Int,
    val tokensPerSec: Float,
    val ctxUsed: Int,
    val ctxTotal: Int,
    /** Сколько токенов промпта взято из KV-кэша (не пересчитывалось). */
    val reused: Int = 0,
)

data class UiMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val streaming: Boolean = false,
    val error: String? = null,
    val attachment: Attachment? = null,
    val stats: GenStats? = null,
)
