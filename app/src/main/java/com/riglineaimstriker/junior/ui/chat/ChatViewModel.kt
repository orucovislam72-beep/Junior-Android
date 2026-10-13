package com.riglineaimstriker.junior.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riglineaimstriker.junior.inference.EngineState
import com.riglineaimstriker.junior.inference.InferenceEngine
import com.riglineaimstriker.junior.inference.InferenceException
import com.riglineaimstriker.junior.inference.PromptBuilder
import com.riglineaimstriker.junior.inference.SkillPrompt
import com.riglineaimstriker.junior.model.AppPrefs
import com.riglineaimstriker.junior.model.ChatStore
import com.riglineaimstriker.junior.model.DeviceTuner
import com.riglineaimstriker.junior.model.ModelRepository
import com.riglineaimstriker.junior.model.Profiles
import com.riglineaimstriker.junior.skills.SkillStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val streaming: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val engine: InferenceEngine,
    private val repo: ModelRepository,
    private val prefs: AppPrefs,
    private val tuner: DeviceTuner,
    private val store: ChatStore,
    private val skills: SkillStore,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<UiMessage>>(store.load())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    val engineState: StateFlow<EngineState> = engine.state

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
    private var ctxSize = 2048

    init {
        viewModelScope.launch {
            prefs.selected.filterNotNull().distinctUntilChanged().collect { loadModel(it) }
        }
    }

    private suspend fun loadModel(id: String) {
        val p = Profiles.byId(id) ?: return
        if (!repo.isInstalled(p)) {
            engine.reportFailure("Файл модели не найден. Скачайте её заново.")
            return
        }
        ctxSize = tuner.contextFor(p)
        engine.load(repo.file(p).path, ctxSize, tuner.threads())
    }

    fun reload() {
        viewModelScope.launch { prefs.selected.value?.let { loadModel(it) } }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || _generating.value || engine.state.value !is EngineState.Ready) return
        _messages.update { it + UiMessage(nextId++, true, t) + UiMessage(nextId++, false, "", streaming = true) }
        generate()
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

    fun newChat() {
        stop()
        _messages.value = emptyList()
        store.clear()
    }

    private fun generate() {
        genJob = viewModelScope.launch {
            _generating.value = true
            val sb = StringBuilder()
            try {
                val history = _messages.value.dropLast(1).map { it.isUser to it.text }
                val rules = skills.activePrompt(ctxSize / 2)
                // Длина ответа и стиль (температура) — из настроек пользователя.
                val maxNew = prefs.maxTokens.value.coerceIn(128, (ctxSize - 256).coerceAtLeast(128))
                val temperature = prefs.style.value.temperature
                val prompt = PromptBuilder.build(SkillPrompt.apply(history, rules), ctxSize - maxNew)
                engine.generate(prompt, maxNew, temperature).collect { piece ->
                    sb.append(piece)
                    updateLast(sb.toString(), streaming = true)
                }
                updateLast(sb.toString(), streaming = false)
            } catch (e: CancellationException) {
                updateLast(sb.toString(), streaming = false)
                throw e
            } catch (e: InferenceException) {
                updateLast(sb.toString(), streaming = false, error = e.message)
            } catch (e: Exception) {
                updateLast(sb.toString(), streaming = false, error = "Не удалось получить ответ. Повторите запрос.")
            } finally {
                _generating.value = false
                store.save(_messages.value)
            }
        }
    }

    private fun updateLast(text: String, streaming: Boolean, error: String? = null) {
        _messages.update { list ->
            if (list.isEmpty()) list
            else list.dropLast(1) + list.last().copy(text = text, streaming = streaming, error = error)
        }
    }

    override fun onCleared() {
        engine.cancel()
        super.onCleared()
    }
}
