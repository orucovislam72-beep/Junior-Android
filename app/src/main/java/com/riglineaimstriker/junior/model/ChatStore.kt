package com.riglineaimstriker.junior.model

import android.content.Context
import com.riglineaimstriker.junior.ui.chat.UiMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Хранит последний чат на устройстве (chat.json), чтобы он переживал перезапуск приложения. */
@Singleton
class ChatStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val file = File(ctx.filesDir, "chat.json")

    private val _preview = MutableStateFlow<String?>(null)
    /** Первая реплика пользователя из сохранённого чата (для кнопки «Продолжить»). */
    val preview: StateFlow<String?> = _preview.asStateFlow()

    /** Запрос, который нужно подставить в поле ввода при открытии чата. */
    var pendingPrompt: String? = null

    init { _preview.value = previewOf(load()) }

    fun takePendingPrompt(): String? {
        val p = pendingPrompt
        pendingPrompt = null
        return p
    }

    fun load(): List<UiMessage> = runCatching {
        val arr = JSONArray(file.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            UiMessage(
                id = o.getLong("id"),
                isUser = o.getBoolean("user"),
                text = o.getString("text"),
                error = if (o.has("error")) o.getString("error") else null,
            )
        }
    }.getOrDefault(emptyList())

    fun save(list: List<UiMessage>) {
        val clean = list.filterNot { !it.isUser && it.text.isEmpty() && it.error == null }
        runCatching {
            if (clean.isEmpty()) {
                file.delete()
            } else {
                val arr = JSONArray()
                clean.forEach { m ->
                    arr.put(JSONObject().apply {
                        put("id", m.id)
                        put("user", m.isUser)
                        put("text", m.text)
                        m.error?.let { put("error", it) }
                    })
                }
                file.writeText(arr.toString())
            }
        }
        _preview.value = previewOf(clean)
    }

    fun clear() = save(emptyList())

    private fun previewOf(list: List<UiMessage>): String? =
        list.firstOrNull { it.isUser }?.text?.trim()?.take(120)?.ifBlank { null }
}
