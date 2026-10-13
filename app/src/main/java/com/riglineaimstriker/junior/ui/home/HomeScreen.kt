package com.riglineaimstriker.junior.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riglineaimstriker.junior.model.AppPrefs
import com.riglineaimstriker.junior.model.ChatStore
import com.riglineaimstriker.junior.model.ModelRepository
import com.riglineaimstriker.junior.model.Profiles
import com.riglineaimstriker.junior.skills.SkillStore
import com.riglineaimstriker.junior.ui.components.JuniorAvatar
import com.riglineaimstriker.junior.ui.theme.Accent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    prefs: AppPrefs,
    private val repo: ModelRepository,
    private val store: ChatStore,
    skills: SkillStore,
) : ViewModel() {

    private fun nameOf(id: String?): String? =
        Profiles.byId(id)?.takeIf { repo.isInstalled(it) }?.name

    val modelName: StateFlow<String?> = prefs.selected
        .map { nameOf(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, nameOf(prefs.selected.value))

    val lastChat: StateFlow<String?> = store.preview

    val activeSkills: StateFlow<Int> = skills.skills
        .map { list -> list.count { it.active } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Начать новый чат: старый удаляется, запрос (если есть) попадёт в поле ввода. */
    fun startNew(prompt: String?) {
        store.clear()
        store.pendingPrompt = prompt
    }
}

private val tasks = listOf(
    Triple("💡", "Объясни код", "Объясни, как работает этот код:\n"),
    Triple("🐞", "Найди баг", "Найди ошибку в этом коде:\n"),
    Triple("⚡", "Оптимизируй", "Оптимизируй этот код:\n"),
    Triple("🧪", "Напиши тесты", "Напиши тесты для этого кода:\n"),
)

@Composable
fun HomeScreen(
    onNewChat: (String?) -> Unit,
    onContinue: () -> Unit,
    onModels: () -> Unit,
    onSkills: () -> Unit,
    onSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val model by vm.modelName.collectAsStateWithLifecycle()
    val last by vm.lastChat.collectAsStateWithLifecycle()
    val skillCount by vm.activeSkills.collectAsStateWithLifecycle()
    val startNew: (String?) -> Unit = { prompt ->
        vm.startNew(prompt)
        onNewChat(prompt)
    }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val t = rememberInfiniteTransition(label = "home")
    val glow by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val pulse by t.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Accent.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width * (0.2f + 0.6f * glow), size.height * 0.10f),
                        radius = size.width * 1.1f,
                    )
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Reveal(shown, 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    JuniorAvatar(
                        size = 84.dp,
                        modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse },
                    )
                    Spacer(Modifier.height(18.dp))
                    Text("Junior", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Ваш ИИ-программист. Работает офлайн.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    StatusChip(model)
                }
            }
            Spacer(Modifier.height(28.dp))

            Reveal(shown, 1) {
                Button(
                    onClick = { startNew(null) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                ) { Text("Новый чат", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            }

            last?.let { preview ->
                Spacer(Modifier.height(12.dp))
                Reveal(shown, 2) { ContinueCard(preview, onContinue) }
            }

            Spacer(Modifier.height(28.dp))
            Reveal(shown, 3) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "Быстрые задачи",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    tasks.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (icon, label, prompt) ->
                                TaskCard(icon, label, Modifier.weight(1f)) { startNew(prompt) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Reveal(shown, 4) {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onSkills,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text("Навыки Rigline (включено: $skillCount)") }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onModels,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text("Модели") }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onSettings,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text("Настройки") }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Всё работает на вашем телефоне, без интернета.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Reveal(shown: Boolean, index: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(500, delayMillis = index * 90)) +
            slideInVertically(tween(500, delayMillis = index * 90)) { it / 3 },
    ) { content() }
}

@Composable
private fun StatusChip(model: String?) {
    Row(
        Modifier
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(7.dp).clip(CircleShape)
                .background(if (model != null) Color(0xFF34C759) else Color(0xFFFF9F0A))
        )
        Text(
            "  " + (model ?: "Модель не выбрана"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContinueCard(preview: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                "Продолжить чат",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                preview,
                Modifier.padding(top = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun TaskCard(icon: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(icon, fontSize = 24.sp)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        }
    }
}
