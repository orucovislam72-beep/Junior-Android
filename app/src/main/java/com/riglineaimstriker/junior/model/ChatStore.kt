package com.riglineaimstriker.junior.model

import android.content.Context
import com.riglineaimstriker.junior.ui.chat.Attachment
import com.riglineaimstriker.junior.ui.chat.GenStats
import com.riglineaimstriker.junior.ui.chat.UiMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Карточка чата в списке истории. */
data class ChatMeta(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAt: Long,
    val messageCount: Int,
    /** true, если название задал пользователь (тогда не перезаписываем его автоматически). */
    val customTitle: Boolean = false,
)

/**
 * История чатов на устройстве: filesDir/chats/index.json (список) + filesDir/chats/<id>.json (сообщения).
 * Старый единственный чат из 0.2.0 (filesDir/chat.json) переносится в историю при первом запуске.
 */
@Singleton
class ChatStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val dir = File(ctx.filesDir, "chats")
    private val indexFile = File(dir, "index.json")
    private val legacyFile = File(ctx.filesDir, "chat.json")

    private val _chats = MutableStateFlow<List<ChatMeta>>(emptyList())
    /** Все чаты, новые сверху. */
    val chats: StateFlow<List<ChatMeta>> = _chats.asStateFlow()

    /** Запрос, который нужно подставить в поле ввода при открытии чата. */
    var pendingPrompt: String? = null

    init {
        dir.mkdirs()
        _chats.value = readIndex()
        migrateLegacy()
    }

    fun takePendingPrompt(): String? {
        val p = pendingPrompt
        pendingPrompt = null
        return p
    }

    /** Новый идентификатор. Чат появится в истории после первого сообщения. */
    fun newId(): String = "c" + System.currentTimeMillis().toString(36) +
        UUID.randomUUID().toString().take(6)

    fun latestId(): String? = _chats.value.firstOrNull()?.id

    fun meta(id: String): ChatMeta? = _chats.value.firstOrNull { it.id == id }

    @Synchronized
    fun load(id: String): List<UiMessage> = runCatching {
        parseMessages(JSONArray(fileOf(id).readText()))
    }.getOrDefault(emptyList())

    /** Сохраняет переписку. Пустой чат удаляется из истории. */
    @Synchronized
    fun save(id: String, list: List<UiMessage>) {
        val clean = list.filterNot { !it.isUser && it.text.isEmpty() && it.error == null }
            .map { it.copy(streaming = false) }
        if (clean.isEmpty()) {
            delete(id)
            return
        }
        runCatching { fileOf(id).writeText(toJson(clean).toString()) }
        val old = meta(id)
        val firstUser = clean.firstOrNull { it.isUser }
        val autoTitle = firstUser?.let { titleFrom(it) } ?: "Новый чат"
        val lastText = clean.last().text.ifBlank { clean.last().attachment?.name.orEmpty() }
        val m = ChatMeta(
            id = id,
            title = if (old?.customTitle == true) old.title else autoTitle,
            preview = lastText.trim().replace('\n', ' ').take(140),
            updatedAt = System.currentTimeMillis(),
            messageCount = clean.size,
            customTitle = old?.customTitle == true,
        )
        _chats.value = (listOf(m) + _chats.value.filter { it.id != id }).sortedByDescending { it.updatedAt }
        writeIndex()
    }

    @Synchronized
    fun rename(id: String, title: String) {
        val t = title.trim().take(80)
        if (t.isEmpty()) return
        _chats.value = _chats.value.map { if (it.id == id) it.copy(title = t, customTitle = true) else it }
        writeIndex()
    }

    @Synchronized
    fun delete(id: String) {
        fileOf(id).delete()
        if (_chats.value.any { it.id == id }) {
            _chats.value = _chats.value.filter { it.id != id }
            writeIndex()
        }
    }

    /** Поиск по названию, превью и тексту сообщений. Пустой запрос — все чаты. */
    suspend fun search(query: String): List<ChatMeta> = withContext(Dispatchers.IO) {
        val q = query.trim()
        val all = _chats.value
        if (q.isEmpty()) return@withContext all
        all.filter { m ->
            m.title.contains(q, ignoreCase = true) ||
                m.preview.contains(q, ignoreCase = true) ||
                load(m.id).any { msg ->
                    msg.text.contains(q, ignoreCase = true) ||
                        msg.attachment?.name?.contains(q, ignoreCase = true) == true
                }
        }
    }

    // ---- внутреннее ----

    private fun fileOf(id: String) = File(dir, id.filter { it.isLetterOrDigit() || it == '-' } + ".json")

    private fun titleFrom(m: UiMessage): String {
        val t = m.text.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val base = t.ifEmpty { m.attachment?.name.orEmpty() }.ifEmpty { "Новый чат" }
        return if (base.length > 48) base.take(47).trimEnd() + "…" else base
    }

    private fun readIndex(): List<ChatMeta> = runCatching {
        val arr = JSONArray(indexFile.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ChatMeta(
                id = o.getString("id"),
                title = o.optString("title", "Чат"),
                preview = o.optString("preview"),
                updatedAt = o.optLong("updatedAt"),
                messageCount = o.optInt("count"),
                customTitle = o.optBoolean("custom"),
            )
        }.filter { fileOf(it.id).exists() }.sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    private fun writeIndex() {
        runCatching {
            val arr = JSONArray()
            _chats.value.forEach { m ->
                arr.put(JSONObject().apply {
                    put("id", m.id)
                    put("title", m.title)
                    put("preview", m.preview)
                    put("updatedAt", m.updatedAt)
                    put("count", m.messageCount)
                    put("custom", m.customTitle)
                })
            }
            val tmp = File(dir, "index.json.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(indexFile)) {
                indexFile.writeText(arr.toString())
                tmp.delete()
            }
        }
    }

    private fun migrateLegacy() {
        if (!legacyFile.exists()) return
        runCatching {
            val msgs = parseMessages(JSONArray(legacyFile.readText()))
            if (msgs.isNotEmpty()) save(newId(), msgs)
        }
        legacyFile.delete()
    }

    private fun parseMessages(arr: JSONArray): List<UiMessage> = List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        UiMessage(
            id = o.getLong("id"),
            isUser = o.getBoolean("user"),
            text = o.getString("text"),
            error = if (o.has("error")) o.getString("error") else null,
            attachment = o.optJSONObject("file")?.let { f ->
                Attachment(
                    name = f.optString("name", "file.txt"),
                    lang = f.optString("lang"),
                    content = f.optString("content"),
                    sizeBytes = f.optLong("size"),
                )
            },
            stats = o.optJSONObject("stats")?.let { s ->
                GenStats(
                    tokens = s.optInt("tokens"),
                    tokensPerSec = s.optDouble("tps", 0.0).toFloat(),
                    ctxUsed = s.optInt("used"),
                    ctxTotal = s.optInt("total"),
                    reused = s.optInt("reused"),
                )
            },
        )
    }

    private fun toJson(list: List<UiMessage>): JSONArray {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("user", m.isUser)
                put("text", m.text)
                m.error?.let { put("error", it) }
                m.attachment?.let { a ->
                    put("file", JSONObject().apply {
                        put("name", a.name)
                        put("lang", a.lang)
                        put("content", a.content)
                        put("size", a.sizeBytes)
                    })
                }
                m.stats?.let { s ->
                    put("stats", JSONObject().apply {
                        put("tokens", s.tokens)
                        put("tps", if (s.tokensPerSec.isFinite()) s.tokensPerSec.toDouble() else 0.0)
                        put("used", s.ctxUsed)
                        put("total", s.ctxTotal)
                        put("reused", s.reused)
                    })
                }
            })
        }
        return arr
    }
}
