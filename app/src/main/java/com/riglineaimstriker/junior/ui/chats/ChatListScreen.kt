package com.riglineaimstriker.junior.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riglineaimstriker.junior.R
import com.riglineaimstriker.junior.model.ChatMeta
import com.riglineaimstriker.junior.model.ChatStore
import com.riglineaimstriker.junior.ui.components.EmptyState
import com.riglineaimstriker.junior.ui.components.JuniorCompactShape
import com.riglineaimstriker.junior.ui.components.JuniorIcon
import com.riglineaimstriker.junior.ui.components.JuniorIcons
import com.riglineaimstriker.junior.ui.components.ScreenTopBar
import com.riglineaimstriker.junior.ui.components.Space
import com.riglineaimstriker.junior.ui.components.formatWhen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatListViewModel @Inject constructor(private val store: ChatStore) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<ChatMeta>> = combine(_query.debounce(200), store.chats) { q, _ -> q }
        .mapLatest { q -> store.search(q) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.chats.value)

    fun setQuery(q: String) { _query.value = q }
    fun rename(id: String, title: String) = store.rename(id, title)
    fun delete(id: String) = store.delete(id)
}

@Composable
fun ChatListScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    vm: ChatListViewModel = hiltViewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val chats by vm.results.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<ChatMeta?>(null) }
    var deleting by remember { mutableStateOf<ChatMeta?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        ScreenTopBar(
            title = "Чаты",
            onBack = onBack,
            actions = {
                IconButton(onClick = onNew) {
                    JuniorIcon(JuniorIcons.NewChat, contentDescription = "Новый чат")
                }
            },
        )
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m),
            singleLine = true,
            shape = JuniorCompactShape,
            placeholder = { Text("Найти в чатах") },
            leadingIcon = { JuniorIcon(JuniorIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) {
                        JuniorIcon(JuniorIcons.Close, contentDescription = "Очистить поиск")
                    }
                }
            },
        )
        when {
            chats.isEmpty() && query.isBlank() -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                EmptyState(
                    illustration = R.drawable.il_no_chats,
                    title = "Пока нет чатов",
                    text = "Каждый разговор сохраняется здесь автоматически. Его можно найти, переименовать или удалить.",
                    action = { Button(onClick = onNew) { Text("Новый чат") } },
                )
            }
            chats.isEmpty() -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                EmptyState(
                    illustration = null,
                    title = "Ничего не нашлось",
                    text = "По запросу «$query» нет совпадений ни в названиях, ни в тексте сообщений.",
                    action = { OutlinedButton(onClick = { vm.setQuery("") }) { Text("Сбросить поиск") } },
                )
            }
            else -> LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = Space.s, end = Space.s, bottom = Space.l),
            ) {
                items(chats, key = { it.id }) { c ->
                    ChatRow(
                        c,
                        onClick = { onOpen(c.id) },
                        onRename = { renaming = c },
                        onDelete = { deleting = c },
                    )
                    HorizontalDivider(
                        Modifier.padding(horizontal = Space.m),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }

    renaming?.let { c ->
        var text by remember(c.id) { mutableStateOf(c.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Переименовать чат") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(80) },
                    singleLine = true, label = { Text("Название") }, shape = JuniorCompactShape,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.rename(c.id, text); renaming = null },
                    enabled = text.isNotBlank(),
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Отмена") } },
        )
    }

    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { JuniorIcon(JuniorIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить чат?") },
            text = { Text("Чат «${c.title}» будет удалён без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(c.id); deleting = null }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ChatRow(c: ChatMeta, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(start = Space.m, top = Space.s, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                c.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (c.preview.isNotBlank()) {
                Text(
                    c.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatWhen(c.updatedAt) + " · сообщений: ${c.messageCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                JuniorIcon(JuniorIcons.More, contentDescription = "Действия с чатом «${c.title}»")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Переименовать") },
                    leadingIcon = { JuniorIcon(JuniorIcons.Edit, contentDescription = null, size = 20.dp) },
                    onClick = { menu = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        JuniorIcon(JuniorIcons.Delete, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}
