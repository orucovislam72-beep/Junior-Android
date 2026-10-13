package com.riglineaimstriker.junior.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riglineaimstriker.junior.model.AnswerStyle
import com.riglineaimstriker.junior.model.AppPrefs
import com.riglineaimstriker.junior.model.ThemeMode
import com.riglineaimstriker.junior.ui.components.JuniorIcon
import com.riglineaimstriker.junior.ui.components.JuniorIcons
import com.riglineaimstriker.junior.ui.components.JuniorShape
import com.riglineaimstriker.junior.ui.components.ScreenTopBar
import com.riglineaimstriker.junior.ui.components.SectionTitle
import com.riglineaimstriker.junior.ui.components.Space
import com.riglineaimstriker.junior.ui.components.formatDecimal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val prefs: AppPrefs) : ViewModel() {
    val style: StateFlow<AnswerStyle> = prefs.style
    val maxTokens: StateFlow<Int> = prefs.maxTokens
    val theme: StateFlow<ThemeMode> = prefs.theme

    fun setStyle(s: AnswerStyle) = prefs.setStyle(s)
    fun setMaxTokens(v: Int) = prefs.setMaxTokens(v)
    fun setTheme(m: ThemeMode) = prefs.setTheme(m)
}

/** Пресеты длины ответа: число токенов → простое название. Числа показываются только в «Подробнее». */
private val lengthPresets = listOf(
    384 to "Короткий",
    768 to "Средний",
    1280 to "Длинный",
    2048 to "Максимальный",
)

private val styleLabels = mapOf(
    AnswerStyle.Precise to ("Точный" to "Стабильнее код и факты"),
    AnswerStyle.Balanced to ("Обычный" to "Подходит для большинства вопросов"),
    AnswerStyle.Creative to ("Свободный" to "Больше разных вариантов и идей"),
)

private val themeLabels = mapOf(
    ThemeMode.System to "Как в телефоне",
    ThemeMode.Light to "Светлая",
    ThemeMode.Dark to "Тёмная",
)

/**
 * Настройки: только существующие AppPrefs (стиль, длина, тема). Экран всегда непустой —
 * пустых и загрузочных состояний у него нет, значения читаются синхронно.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val style by vm.style.collectAsStateWithLifecycle()
    val maxTokens by vm.maxTokens.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    var details by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScreenTopBar(title = "Настройки", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l, vertical = Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            SectionTitle("Как отвечать")
            Column(Modifier.selectableGroup()) {
                AnswerStyle.entries.forEach { s ->
                    val (label, hint) = styleLabels.getValue(s)
                    Option(label, hint, selected = s == style, onClick = { vm.setStyle(s) })
                }
            }

            Spacer(Modifier.heightIn(min = Space.l))
            SectionTitle("Длина ответа")
            Text(
                "Длинные ответы подробнее, но пишутся дольше.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.selectableGroup()) {
                lengthPresets.forEach { (tokens, label) ->
                    Option(label, null, selected = tokens == maxTokens, onClick = { vm.setMaxTokens(tokens) })
                }
            }

            Spacer(Modifier.heightIn(min = Space.l))
            SectionTitle("Оформление")
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { m ->
                    Option(themeLabels.getValue(m), null, selected = m == theme, onClick = { vm.setTheme(m) })
                }
            }

            Spacer(Modifier.heightIn(min = Space.l))
            TextButton(onClick = { details = !details }) {
                Text(if (details) "Скрыть подробности" else "Подробнее")
                Spacer(Modifier.width(Space.xs))
                JuniorIcon(
                    if (details) JuniorIcons.ChevronDown else JuniorIcons.ChevronRight,
                    contentDescription = null, size = 20.dp,
                )
            }
            if (details) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(JuniorShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(Space.l),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    Detail("Температура сэмплирования", formatDecimal(style.temperature))
                    Detail("Максимум токенов в ответе", maxTokens.toString())
                    lengthPresets.forEach { (t, l) -> Detail("«$l»", "до $t токенов") }
                }
            }

            Text(
                "Настройки сохраняются сразу и действуют для следующих ответов.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.l, bottom = Space.xl),
            )
        }
    }
}

@Composable
private fun Option(label: String, hint: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(JuniorShape)
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.primary, JuniorShape)
                else Modifier
            )
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = Space.s, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Space.s))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = com.riglineaimstriker.junior.ui.theme.JuniorCodeFont))
    }
}
