package com.riglineaimstriker.junior.ui.chat

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riglineaimstriker.junior.inference.EngineState
import com.riglineaimstriker.junior.inference.GenEvent
import com.riglineaimstriker.junior.inference.InferenceException
import com.riglineaimstriker.junior.inference.ModelSession
import com.riglineaimstriker.junior.inference.PromptBuilder
import com.riglineaimstriker.junior.inference.Prompts
import com.riglineaimstriker.junior.model.AppPrefs
import com.riglineaimstriker.junior.model.ChatStore
import com.riglineaimstriker.junior.model.Profiles
import com.riglineaimstriker.junior.skills.SkillStore
import com.riglineaimstriker.junior.util.CodeLang
import com.riglineaimstriker.junior.util.TextFiles
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val session: ModelSession,
    private val prefs: AppPrefs,
    private val store: ChatStore,
    private val skills: SkillStore,
    @ApplicationContext private val appCtx: Context,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val engine = session.engine

    private var chatId: String = savedState.get<String>("id")
        ?.takeIf { it.isNotBlank() && it != NEW_CHAT } ?: store.newId()
    private val _chatId = MutableStateFlow(chatId)

    private val _messages = MutableStateFlow(store.load(chatId))
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    /** Что модель делает прямо сейчас («Читаю файл…», «Думаю…», «Пишу код…»). */
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    /** Файл, приложенный к следующему сообщению. */
    private val _attachment = MutableStateFlow<Attachment?>(null)
    val attachment: StateFlow<Attachment?> = _attachment.asStateFlow()

    /** id сообщения пользователя, которое сейчас редактируется. */
    private val _editingId = MutableStateFlow<Long?>(null)
    val editingId: StateFlow<Long?> = _editingId.asStateFlow()

    /** Короткое уведомление (ошибка файла, нет распознавания речи…). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    val engineState: StateFlow<EngineState> = engine.state

    val title: StateFlow<String> = combine(store.chats, _chatId) { list, id ->
        list.firstOrNull { it.id == id }?.title ?: "Новый чат"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, store.meta(chatId)?.title ?: "Новый чат")

    /** Запрос из быстрых задач главного меню: подставляется в поле ввода. */
    val initialPrompt: String = store.takePendingPrompt().orEmpty()

    val modelName: StateFlow<String> = prefs.selected
        .map { Profiles.byId(it)?.name ?: "Junior" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Junior")

    val hasModel: StateFlow<Boolean> = prefs.selected
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, prefs.selected.value != null)

    private var genJob: Job? = null
    private var nextId = (_messages.value.maxOfOrNull { it.id } ?: -1L) + 1

    init {
        viewModelScope.launch {
            prefs.selected.filterNotNull().distinctUntilChanged().collect { session.load(it) }
        }
    }

    fun reload() {
        viewModelScope.launch { session.load() }
    }

    fun dismissNotice() { _notice.value = null }
    fun showNotice(text: String) { _notice.value = text }

    // ---- вложения ----

    fun attach(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { TextFiles.read(appCtx, uri) } }
            result.onSuccess { f ->
                _attachment.value = Attachment(
                    name = f.name,
                    lang = CodeLang.fromFileName(f.name),
                    content = f.text,
                    sizeBytes = f.sizeBytes,
                )
                val budget = session.attachmentBudgetChars()
                if (f.text.length > budget) {
                    _notice.value = "Файл большой: модель увидит примерно первые $budget символов из ${f.text.length}."
                }
            }.onFailure { e ->
                _notice.value = (e as? IllegalArgumentException)?.message ?: "Не удалось прочитать файл."
            }
        }
    }

    fun removeAttachment() { _attachment.value = null }

    // ---- редактирование ----

    /** Начать правку своего сообщения: вернёт текст для поля ввода. */
    fun startEdit(id: Long): String? {
        if (_generating.value) return null
        val m = _messages.value.firstOrNull { it.id == id && it.isUser } ?: return null
        _editingId.value = id
        _attachment.value = m.attachment
        return m.text
    }

    fun cancelEdit() {
        _editingId.value = null
        _attachment.value = null
    }

    // ---- отправка ----

    fun send(text: String): Boolean {
        val t = text.trim()
        val att = _attachment.value
        if ((t.isEmpty() && att == null) || _generating.value || engine.state.value !is EngineState.Ready) return false
        val editId = _editingId.value
        _messages.update { list ->
            val base = if (editId != null) {
                val idx = list.indexOfFirst { it.id == editId }
                if (idx >= 0) list.take(idx) else list
            } else list
            base + UiMessage(nextId++, true, t, attachment = att) +
                UiMessage(nextId++, false, "", streaming = true)
        }
        _editingId.value = null
        _attachment.value = null
        // Сохраняем сразу: вопрос не потеряется, даже если приложение закроют во время ответа.
        store.save(chatId, _messages.value)
        generate()
        return true
    }

    fun regenerate() {
        if (_generating.value || engine.state.value !is EngineState.Ready) return
        val list = _messages.value
        if (list.size < 2 || list.last().isUser) return
        _messages.value = list.dropLast(1) + UiMessage(nextId++, false, "", streaming = true)
        generate()
    }

    fun stop() {
        genJob?.cancel()
        engine.cancel()
    }

    /** Новый чат в этом же экране. Старый остаётся в истории. */
    fun newChat() {
        if (_generating.value) {
            stop()
            // Недописанный ответ сохраняем в старый чат: finally генерации увидит уже новый chatId.
            store.save(chatId, _messages.value)
        }
        chatId = store.newId()
        _chatId.value = chatId
        _messages.value = emptyList()
        _attachment.value = null
        _editingId.value = null
        nextId = 0
    }

    fun rename(title: String) = store.rename(chatId, title)

    private fun promptText(m: UiMessage): String {
        val a = m.attachment ?: return m.text
        val budget = session.attachmentBudgetChars()
        val body = if (a.content.length > budget) {
            a.content.take(budget) + "\n…[файл обрезан: показано $budget из ${a.content.length} символов]"
        } else a.content
        val question = m.text.ifBlank {
            "Разбери этот файл: что он делает, какие в нём ошибки и что стоит улучшить."
        }
        val fence = a.lang.takeIf { it != "text" }.orEmpty()
        return "Файл `${a.name}`:\n```$fence\n$body\n```\n\n$question"
    }

    private fun generate() {
        genJob = viewModelScope.launch {
            _generating.value = true
            _status.value = "Готовлю запрос…"
            val sb = StringBuilder()
            var stats: GenStats? = null
            val requestId = chatId
            try {
                val ctx = session.ctxSize
                val maxNew = session.maxNewTokens()
                val temperature = prefs.style.value.temperature
                val system = Prompts.withSkills(Prompts.ASSISTANT, skills.activePrompt(ctx / 2))
                val msgs = _messages.value.dropLast(1)
                val history = msgs.map { it.isUser to promptText(it) }
                val fileName = msgs.lastOrNull { it.isUser }?.attachment?.name
                val prompt = withContext(Dispatchers.Default) {
                    session.fit({ budget -> PromptBuilder.build(system, history, budget) }, ctx - maxNew - 8)
                }

                var tokens = 0
                var firstAt = 0L
                var nPrompt = 0
                var reused = 0
                var nCtx = ctx
                engine.generate(prompt, maxNew, temperature).collect { ev ->
                    when (ev) {
                        is GenEvent.Prompt -> {
                            nPrompt = ev.nPrompt; reused = ev.nReused; nCtx = ev.nCtx
                            val todo = (ev.nPrompt - ev.nReused).coerceAtLeast(1)
                            val pct = ((ev.nDone - ev.nReused) * 100 / todo).coerceIn(0, 100)
                            _status.value = when {
                                ev.nDone >= ev.nPrompt -> "Думаю…"
                                fileName != null -> "Читаю файл $fileName… $pct%"
                                else -> "Читаю контекст… $pct%"
                            }
                            stats = GenStats(0, 0f, ev.nDone, ev.nCtx, ev.nReused)
                            updateLast(sb.toString(), streaming = true, stats = stats)
                        }
                        is GenEvent.Token -> {
                            val now = SystemClock.elapsedRealtime()
                            tokens++
                            if (tokens == 1) firstAt = now
                            sb.append(ev.text)
                            val dt = now - firstAt
                            val tps = if (tokens > 1 && dt > 0) (tokens - 1) * 1000f / dt else 0f
                            stats = GenStats(tokens, tps, nPrompt + tokens, nCtx, reused)
                            _status.value = if (insideCode(sb)) "Пишу код…" else "Пишу ответ…"
                            updateLast(sb.toString(), streaming = true, stats = stats)
                        }
                    }
                }
                updateLast(sb.toString(), streaming = false, stats = stats)
            } catch (e: CancellationException) {
                updateLast(sb.toString(), streaming = false, stats = stats)
                throw e
            } catch (e: InferenceException) {
                if (e.code == InferenceException.CONTEXT_OVERFLOW) engine.resetCache()
                updateLast(sb.toString(), streaming = false, error = e.message, stats = stats)
            } catch (e: Exception) {
                updateLast(
                    sb.toString(), streaming = false, stats = stats,
                    error = "Не удалось получить ответ. Повторите запрос.",
                )
            } finally {
                _generating.value = false
                _status.value = null
                if (requestId == chatId) store.save(chatId, _messages.value)
            }
        }
    }

    /** Внутри незакрытого блока ``` — значит, модель сейчас пишет код. */
    private fun insideCode(sb: StringBuilder): Boolean {
        var count = 0
        var i = sb.indexOf("```")
        while (i >= 0) {
            count++
            i = sb.indexOf("```", i + 3)
        }
        return count % 2 == 1
    }

    private fun updateLast(text: String, streaming: Boolean, error: String? = null, stats: GenStats? = null) {
        _messages.update { list ->
            if (list.isEmpty() || list.last().isUser) list
            else list.dropLast(1) + list.last().copy(
                text = text, streaming = streaming, error = error, stats = stats ?: list.last().stats,
            )
        }
    }

    override fun onCleared() {
        engine.cancel()
        super.onCleared()
    }

    companion object {
        /** Значение аргумента маршрута для нового чата. */
        const val NEW_CHAT = "new"
    }
}
