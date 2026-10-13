package com.riglineaimstriker.junior.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riglineaimstriker.junior.inference.EngineState
import com.riglineaimstriker.junior.inference.GenEvent
import com.riglineaimstriker.junior.inference.InferenceException
import com.riglineaimstriker.junior.inference.ModelSession
import com.riglineaimstriker.junior.inference.PromptBuilder
import com.riglineaimstriker.junior.inference.Prompts
import com.riglineaimstriker.junior.model.AppPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class StepKind { Task, Tool, Answer, Note }
enum class StepStatus { Running, Waiting, Done, Failed, Rejected }

/** Карточка шага агента. */
data class AgentStep(
    val id: Int,
    val kind: StepKind,
    val title: String,
    val tool: String? = null,
    val path: String? = null,
    /** Короткий результат / текст ответа. */
    val detail: String = "",
    val status: StepStatus = StepStatus.Done,
    /** Дифф для write_file. */
    val diff: List<DiffLine>? = null,
)

/** Запись файла, ждущая подтверждения пользователя. */
data class PendingWrite(val path: String, val isNew: Boolean, val diff: List<DiffLine>, val lines: Int)

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val session: ModelSession,
    private val prefs: AppPrefs,
    @ApplicationContext private val appCtx: Context,
) : ViewModel() {
    private val engine = session.engine
    val engineState: StateFlow<EngineState> = engine.state

    private val _workspace = MutableStateFlow<Workspace?>(null)
    val workspace: StateFlow<Workspace?> = _workspace.asStateFlow()

    private val _steps = MutableStateFlow<List<AgentStep>>(emptyList())
    val steps: StateFlow<List<AgentStep>> = _steps.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Что агент делает сейчас (строка статуса над полем ввода). */
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    /** «Разрешать всё в этой сессии»: запись файлов без подтверждения. */
    private val _autoApprove = MutableStateFlow(false)
    val autoApprove: StateFlow<Boolean> = _autoApprove.asStateFlow()

    private val _pending = MutableStateFlow<PendingWrite?>(null)
    val pending: StateFlow<PendingWrite?> = _pending.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var decision: CompletableDeferred<Boolean>? = null
    private var job: Job? = null
    private var nextStepId = 0

    init {
        openSaved()
        viewModelScope.launch { session.load() }
    }

    private fun openSaved() {
        val saved = prefs.workspace.value ?: return
        _workspace.value = runCatching { open(saved) }.getOrElse {
            _notice.value = "Нет доступа к сохранённой папке. Выберите её заново."
            prefs.setWorkspace(null)
            null
        }
    }

    private fun open(value: String): Workspace =
        if (value == AppPrefs.WORKSPACE_INTERNAL) {
            FileWorkspace(File(appCtx.filesDir, "workspace"), "Папка Junior (внутренняя)")
        } else {
            val uri = Uri.parse(value)
            val granted = appCtx.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
            require(granted) { "Нет разрешения на папку." }
            SafWorkspace(appCtx, uri)
        }

    fun useInternal() {
        prefs.setWorkspace(AppPrefs.WORKSPACE_INTERNAL)
        _workspace.value = open(AppPrefs.WORKSPACE_INTERNAL)
    }

    fun useTree(uri: Uri) {
        runCatching {
            appCtx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            prefs.setWorkspace(uri.toString())
            _workspace.value = SafWorkspace(appCtx, uri)
        }.onFailure { _notice.value = "Не удалось открыть папку: ${it.message ?: "нет доступа"}" }
    }

    fun changeFolder() {
        if (_running.value) return
        prefs.setWorkspace(null)
        _workspace.value = null
    }

    fun setAutoApprove(v: Boolean) { _autoApprove.value = v }

    fun dismissNotice() { _notice.value = null }

    fun decide(approve: Boolean, allowAll: Boolean = false) {
        if (allowAll) _autoApprove.value = true
        decision?.complete(approve)
    }

    fun stop() {
        job?.cancel()
        engine.cancel()
        decision?.complete(false)
    }

    fun clear() {
        if (_running.value) return
        _steps.value = emptyList()
    }

    fun run(task: String) {
        val t = task.trim()
        val ws = _workspace.value ?: return
        if (t.isEmpty() || _running.value || engine.state.value !is EngineState.Ready) return
        _steps.value = emptyList()
        addStep(AgentStep(nextStepId++, StepKind.Task, "Задача", detail = t))
        job = viewModelScope.launch {
            _running.value = true
            try {
                loop(ws, t)
            } catch (e: CancellationException) {
                markRunningAs(StepStatus.Failed, "Остановлено")
                addStep(AgentStep(nextStepId++, StepKind.Note, "Остановлено пользователем"))
                throw e
            } catch (e: Exception) {
                markRunningAs(StepStatus.Failed, e.message ?: "Ошибка")
                addStep(AgentStep(nextStepId++, StepKind.Note, "Ошибка", detail = e.message ?: "Неизвестная ошибка"))
            } finally {
                _running.value = false
                _status.value = null
                _pending.value = null
                decision = null
            }
        }
    }

    private suspend fun loop(ws: Workspace, task: String) {
        val runner = ToolRunner(ws)
        val ctx = session.ctxSize
        // Под ответ шага — ~30% контекста: хватает на write_file среднего файла
        // и оставляет место для системного промпта (~650 токенов) и истории шагов.
        val maxNew = (ctx * 3 / 10).coerceIn(512, 1536)
        val system = Prompts.AGENT + "\n\nПроект: «${ws.title}»."
        val convo = mutableListOf(true to task)

        for (step in 1..MAX_STEPS) {
            val thinkingId = nextStepId++
            addStep(AgentStep(thinkingId, StepKind.Answer, "Думаю…", status = StepStatus.Running))
            val history = compact(convo)
            val prompt = withContext(Dispatchers.Default) {
                session.fit({ b -> PromptBuilder.build(system, history, b, keepFirst = true) }, ctx - maxNew - 8)
            }
            val text = generate(prompt, maxNew, thinkingId)
            val call = ToolParser.parse(text, final = true)

            if (call == null || call.name !in ToolParser.TOOLS) {
                if (call != null) {
                    // Неизвестный инструмент — подсказываем модели и идём дальше.
                    removeStep(thinkingId)
                    addStep(AgentStep(nextStepId++, StepKind.Tool, call.name, call.name, call.path,
                        "Неизвестный инструмент", StepStatus.Failed))
                    convo += false to call.raw
                    convo += true to "<result>\nОшибка: неизвестный инструмент «${call.name}». " +
                        "Доступны: ${ToolParser.TOOLS.joinToString()}.\n</result>"
                    continue
                }
                val answer = ToolParser.stripTags(text).ifBlank { "Готово." }
                updateStep(thinkingId) { it.copy(title = "Ответ", detail = answer, status = StepStatus.Done) }
                return
            }

            removeStep(thinkingId)
            val toolId = nextStepId++
            addStep(
                AgentStep(toolId, StepKind.Tool, toolTitle(call), call.name, WorkspacePaths.normalizeOrRaw(call.path),
                    status = StepStatus.Running)
            )
            convo += false to call.raw
            _status.value = "${toolTitle(call)}…"

            val result: String = try {
                if (call.name == "write_file") {
                    val approved = confirmWrite(ws, call, toolId)
                    if (!approved) {
                        updateStep(toolId) { it.copy(status = StepStatus.Rejected, detail = "Запись отклонена") }
                        "Пользователь отклонил запись файла ${call.path}. Предложи другой вариант или закончи ответом."
                    } else {
                        val r = withContext(Dispatchers.IO) { runner.run(call) }
                        updateStep(toolId) { it.copy(status = StepStatus.Done, detail = r) }
                        r
                    }
                } else {
                    val r = withContext(Dispatchers.IO) { runner.run(call) }
                    updateStep(toolId) { it.copy(status = StepStatus.Done, detail = shortResult(call, r)) }
                    r
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "ошибка"
                updateStep(toolId) { it.copy(status = StepStatus.Failed, detail = msg) }
                "Ошибка: $msg"
            }
            val last = step == MAX_STEPS - 1
            convo += true to "<result>\n$result\n</result>" +
                if (last) "\nЭто последний шаг: ответь итогом обычным текстом, без инструментов." else ""
        }
        addStep(AgentStep(nextStepId++, StepKind.Note, "Достигнут лимит в $MAX_STEPS шагов",
            detail = "Уточните задачу или разбейте её на части."))
    }

    /** Генерация одного шага: стримим текст в карточку, останавливаемся на законченном теге. */
    private suspend fun generate(prompt: String, maxNew: Int, cardId: Int): String {
        val sb = StringBuilder()
        var tokens = 0
        var firstAt = 0L
        try {
            engine.generate(prompt, maxNew, TEMPERATURE).collect { ev ->
                when (ev) {
                    is GenEvent.Prompt -> {
                        val todo = (ev.nPrompt - ev.nReused).coerceAtLeast(1)
                        val pct = ((ev.nDone - ev.nReused) * 100 / todo).coerceIn(0, 100)
                        _status.value = if (ev.nDone >= ev.nPrompt) "Думаю… · контекст ${ev.nPrompt}/${ev.nCtx}"
                        else "Читаю контекст… $pct%"
                    }
                    is GenEvent.Token -> {
                        val now = SystemClock.elapsedRealtime()
                        tokens++
                        if (tokens == 1) firstAt = now
                        sb.append(ev.text)
                        val tps = if (tokens > 1 && now > firstAt) (tokens - 1) * 1000f / (now - firstAt) else 0f
                        val s = sb.toString()
                        val writing = s.contains("<tool")
                        _status.value = (if (writing) "Готовлю действие…" else "Пишу ответ…") +
                            String.format(java.util.Locale.getDefault(), " · %.1f ток/с", tps)
                        updateStep(cardId) { it.copy(detail = s) }
                        if (ToolParser.isComplete(s)) throw StopGeneration()
                    }
                }
            }
        } catch (_: StopGeneration) {
            // Тег закрыт — дальше модель не нужна.
        } catch (e: InferenceException) {
            if (e.code == InferenceException.CONTEXT_OVERFLOW) engine.resetCache()
            throw IllegalStateException(e.message)
        }
        return sb.toString()
    }

    private suspend fun confirmWrite(ws: Workspace, call: ToolCall, stepId: Int): Boolean {
        val old = withContext(Dispatchers.IO) { runCatching { ws.readOrNull(call.path) }.getOrNull() }
        val diff = withContext(Dispatchers.Default) { LineDiff.diff(old, call.content) }
        updateStep(stepId) {
            it.copy(diff = diff, detail = if (old == null) "Новый файл" else "Изменение файла")
        }
        if (_autoApprove.value) return true
        updateStep(stepId) { it.copy(status = StepStatus.Waiting) }
        _status.value = "Жду подтверждения записи…"
        val d = CompletableDeferred<Boolean>()
        decision = d
        _pending.value = PendingWrite(WorkspacePaths.normalizeOrRaw(call.path), old == null, diff, ToolRunner.lineCount(call.content))
        return try {
            d.await()
        } finally {
            _pending.value = null
            decision = null
            updateStep(stepId) { if (it.status == StepStatus.Waiting) it.copy(status = StepStatus.Running) else it }
        }
    }

    /** Старые результаты инструментов сокращаем, последние два оставляем целиком. */
    private fun compact(convo: List<Pair<Boolean, String>>): List<Pair<Boolean, String>> {
        val resultIdx = convo.indices.filter { i -> i > 0 && convo[i].first }
        val keepFull = resultIdx.takeLast(2).toSet()
        return convo.mapIndexed { i, p ->
            if (i in resultIdx && i !in keepFull && p.second.length > 700) {
                p.first to (p.second.take(600) + "\n…[сокращено]\n</result>")
            } else p
        }
    }

    private fun toolTitle(c: ToolCall): String = when (c.name) {
        "list_files" -> "Смотрю файлы"
        "read_file" -> "Читаю файл"
        "write_file" -> "Пишу файл"
        "create_dir" -> "Создаю папку"
        "search" -> "Ищу «${c.query.take(30)}»"
        else -> c.name
    }

    private fun shortResult(c: ToolCall, r: String): String = when (c.name) {
        "read_file" -> "Прочитано: строк ${ToolRunner.lineCount(r)}, символов ${r.length}"
        "list_files" -> r.lines().take(12).joinToString("\n") + if (r.lines().size > 12) "\n… ещё ${r.lines().size - 12}" else ""
        "search" -> r.lines().take(8).joinToString("\n") + if (r.lines().size > 8) "\n… ещё ${r.lines().size - 8}" else ""
        else -> r
    }

    private fun addStep(s: AgentStep) = _steps.update { it + s }
    private fun removeStep(id: Int) = _steps.update { list -> list.filterNot { it.id == id } }
    private fun updateStep(id: Int, f: (AgentStep) -> AgentStep) =
        _steps.update { list -> list.map { if (it.id == id) f(it) else it } }

    private fun markRunningAs(status: StepStatus, detail: String) = _steps.update { list ->
        list.map {
            if (it.status == StepStatus.Running || it.status == StepStatus.Waiting) {
                it.copy(status = status, detail = it.detail.ifBlank { detail })
            } else it
        }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private class StopGeneration : Exception()

    companion object {
        const val MAX_STEPS = 8
        private const val TEMPERATURE = 0.2f
    }
}
