package com.riglineaimstriker.junior.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riglineaimstriker.junior.model.AnswerStyle
import com.riglineaimstriker.junior.model.AppPrefs
import com.riglineaimstriker.junior.model.ThemeMode
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

/** Пресеты длины ответа: подпись к количеству токенов. */
private val lengthPresets = listOf(
    384 to "Короткий",
    768 to "Средний",
    1280 to "Длинный",
    2048 to "Максимум",
)

private val styleLabels = mapOf(
    AnswerStyle.Precise to "Точный",
    AnswerStyle.Balanced to "Сбалансированный",
    AnswerStyle.Creative to "Творческий",
)

private val themeLabels = mapOf(
    ThemeMode.System to "Системная",
    ThemeMode.Light to "Светлая",
    ThemeMode.Dark to "Тёмная",
)

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val style by vm.style.collectAsStateWithLifecycle()
    val maxTokens by vm.maxTokens.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Настройки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
        ) {
            Section(
                title = "Стиль ответа",
                hint = "Точный — стабильнее код и факты. Творческий — больше вариантов.",
            ) {
                AnswerStyle.entries.forEach { s ->
                    OptionRow(
                        label = styleLabels.getValue(s),
                        selected = s == style,
                        onClick = { vm.setStyle(s) },
                    )
                }
            }

            Section(
                title = "Длина ответа",
                hint = "Сколько текста Junior пишет за раз. Длиннее — медленнее.",
            ) {
                lengthPresets.forEach { (tokens, label) ->
                    OptionRow(
                        label = "$label (~$tokens токенов)",
                        selected = tokens == maxTokens,
                        onClick = { vm.setMaxTokens(tokens) },
                    )
                }
            }

            Section(
                title = "Тема оформления",
                hint = "Системная повторяет настройку телефона.",
            ) {
                ThemeMode.entries.forEach { m ->
                    OptionRow(
                        label = themeLabels.getValue(m),
                        selected = m == theme,
                        onClick = { vm.setTheme(m) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Всё работает на вашем телефоне, офлайн. Настройки сохраняются автоматически.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, hint: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(18.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(
        hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Text("✓", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
