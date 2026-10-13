package com.riglineaimstriker.junior.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Стиль ответа: влияет на температуру сэмплирования. */
enum class AnswerStyle(val temperature: Float) {
    Precise(0.2f),   // Точный: меньше фантазии, стабильнее код
    Balanced(0.4f),  // Сбалансированный
    Creative(0.7f);  // Творческий: больше вариантов

    companion object {
        fun from(name: String?): AnswerStyle =
            AnswerStyle.entries.firstOrNull { it.name == name } ?: Balanced
    }
}

/** Тема оформления. */
enum class ThemeMode {
    System, Light, Dark;

    companion object {
        fun from(name: String?): ThemeMode =
            ThemeMode.entries.firstOrNull { it.name == name } ?: System
    }
}

@Singleton
class AppPrefs @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("junior", Context.MODE_PRIVATE)

    private val _selected = MutableStateFlow(sp.getString(KEY_MODEL, null))
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val _style = MutableStateFlow(AnswerStyle.from(sp.getString(KEY_STYLE, null)))
    /** Стиль ответа (температура). */
    val style: StateFlow<AnswerStyle> = _style.asStateFlow()

    private val _maxTokens = MutableStateFlow(sp.getInt(KEY_MAX_TOKENS, DEFAULT_MAX_TOKENS))
    /** Максимальная длина ответа в токенах. */
    val maxTokens: StateFlow<Int> = _maxTokens.asStateFlow()

    private val _theme = MutableStateFlow(ThemeMode.from(sp.getString(KEY_THEME, null)))
    /** Тема оформления. */
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _workspace = MutableStateFlow(sp.getString(KEY_WORKSPACE, null))
    /** Папка проекта агента Rigline Code: [WORKSPACE_INTERNAL] или Uri дерева SAF. */
    val workspace: StateFlow<String?> = _workspace.asStateFlow()

    fun setWorkspace(value: String?) {
        sp.edit().apply { if (value == null) remove(KEY_WORKSPACE) else putString(KEY_WORKSPACE, value) }.apply()
        _workspace.value = value
    }

    fun select(id: String) {
        sp.edit().putString(KEY_MODEL, id).apply()
        _selected.value = id
    }

    fun clear() {
        sp.edit().remove(KEY_MODEL).apply()
        _selected.value = null
    }

    fun setStyle(s: AnswerStyle) {
        sp.edit().putString(KEY_STYLE, s.name).apply()
        _style.value = s
    }

    fun setMaxTokens(value: Int) {
        val v = value.coerceIn(MIN_MAX_TOKENS, MAX_MAX_TOKENS)
        sp.edit().putInt(KEY_MAX_TOKENS, v).apply()
        _maxTokens.value = v
    }

    fun setTheme(mode: ThemeMode) {
        sp.edit().putString(KEY_THEME, mode.name).apply()
        _theme.value = mode
    }

    companion object {
        const val DEFAULT_MAX_TOKENS = 768
        const val MIN_MAX_TOKENS = 256
        const val MAX_MAX_TOKENS = 2048

        private const val KEY_MODEL = "selected_model"
        private const val KEY_STYLE = "answer_style"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_WORKSPACE = "agent_workspace"

        const val WORKSPACE_INTERNAL = "internal"
    }
}
