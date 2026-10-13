package com.riglineaimstriker.junior.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riglineaimstriker.junior.model.AppPrefs
import com.riglineaimstriker.junior.model.ChatMeta
import com.riglineaimstriker.junior.model.ChatStore
import com.riglineaimstriker.junior.model.ModelRepository
import com.riglineaimstriker.junior.model.Profiles
import com.riglineaimstriker.junior.skills.SkillStore
import com.riglineaimstriker.junior.ui.chats.ChatListViewModel
import com.riglineaimstriker.junior.ui.components.InlineNotice
import com.riglineaimstriker.junior.ui.components.JuniorCompactShape
import com.riglineaimstriker.junior.ui.components.JuniorIcon
import com.riglineaimstriker.junior.ui.components.JuniorIcons
import com.riglineaimstriker.junior.ui.components.NavRow
import com.riglineaimstriker.junior.ui.components.ScreenTopBar
import com.riglineaimstriker.junior.ui.components.SectionTitle
import com.riglineaimstriker.junior.ui.components.Space
import com.riglineaimstriker.junior.ui.components.formatWhen
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

    /** Последний чат: id и заголовок (для карточки «Продолжить»). */
    val lastChat: StateFlow<ChatMeta?> = store.chats
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.chats.value.firstOrNull())

    val chatCount: StateFlow<Int> = store.chats
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.chats.value.size)

    val activeSkills: StateFlow<Int> = skills.skills
        .map { list -> list.count { it.active } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Начать новый чат: старые остаются в истории, запрос (если есть) попадёт в поле ввода. */
    fun startNew(prompt: String?) {
        store.pendingPrompt = prompt
    }
}

/** Сколько последних чатов показывать на главном экране. */
private const val RECENT_LIMIT = 5

/**
 * Главный экран: одно главное действие («Новый чат»), тихий список недавних
 * разговоров и второстепенные разделы. Без «героя» и декоративных картинок.
 */
@Composable
fun HomeScreen(
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onChats: () -> Unit,
    onAgent: () -> Unit,
    onModels: () -> Unit,
    onSkills: () -> Unit,
    onSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
    listVm: ChatListViewModel = hiltViewModel(),
) {
    val model by vm.modelName.collectAsStateWithLifecycle()
    val skillCount by vm.activeSkills.collectAsStateWithLifecycle()
    val chatCount by vm.chatCount.collectAsStateWithLifecycle()
    // Существующий список чатов из ChatListViewModel (пустой запрос = все чаты, новые сверху).
    val chats by listVm.results.collectAsStateWithLifecycle()
    val recent = chats.take(RECENT_LIMIT)

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScreenTopBar(
            title = "Junior",
            subtitle = model?.let { "$it · работает без интернета" } ?: "Модель не выбрана",
            actions = {
                IconButton(onClick = onSettings) {
                    JuniorIcon(JuniorIcons.Settings, contentDescription = "Настройки")
                }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            item {
                Button(
                    onClick = {
                        vm.startNew(null)
                        onNewChat()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    JuniorIcon(JuniorIcons.NewChat, contentDescription = null, size = 20.dp,
                        tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(Space.s))
                    Text("Новый чат", style = MaterialTheme.typography.titleSmall)
                }
            }
            if (model == null) {
                item {
                    InlineNotice(
                        "Чтобы начать, выберите модель. Она работает прямо на телефоне.",
                        isError = false,
                        action = { TextButton(onClick = onModels) { Text("Выбрать") } },
                    )
                }
            }

            item {
                Spacer(Modifier.height(Space.l))
                SectionTitle("Недавние")
            }
            if (recent.isEmpty()) {
                item {
                    Text(
                        "Здесь появятся ваши разговоры. Начните новый чат — он сохранится автоматически.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Space.s),
                    )
                }
            } else {
                items(recent, key = { it.id }) { c -> RecentRow(c, onClick = { onOpenChat(c.id) }) }
                if (chatCount > recent.size) {
                    item {
                        TextButton(onClick = onChats) { Text("Показать все чаты ($chatCount)") }
                    }
                }
            }

            item {
                Spacer(Modifier.height(Space.l))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Space.s))
            }
            item {
                NavRow(
                    JuniorIcons.Agent, "Агент для проекта",
                    "Читает и правит файлы в выбранной папке, каждое изменение — с вашего согласия",
                    onClick = onAgent,
                )
            }
            item {
                NavRow(
                    JuniorIcons.Chat, "Все чаты",
                    if (chatCount > 0) "Сохранено: $chatCount" else "Поиск, переименование, удаление",
                    onClick = onChats,
                )
            }
            item {
                NavRow(
                    JuniorIcons.Skills, "Навыки",
                    if (skillCount > 0) "Включено: $skillCount" else "Правила, которые Junior соблюдает в ответах",
                    onClick = onSkills,
                )
            }
            item {
                NavRow(JuniorIcons.Download, "Модели", model ?: "Скачать или выбрать модель", onClick = onModels)
            }
            item {
                Text(
                    "Модель работает на вашем телефоне: ответы считаются локально, без облачного сервера.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.l, start = Space.m),
                )
            }
        }
    }
}

@Composable
private fun RecentRow(c: ChatMeta, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(JuniorCompactShape)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.m, vertical = Space.s),
    ) {
        Text(
            c.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val time = formatWhen(c.updatedAt)
        Text(
            if (c.preview.isNotBlank()) "$time · ${c.preview}" else time,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
