package com.riglineaimstriker.junior.ui.skills

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riglineaimstriker.junior.skills.Skill
import com.riglineaimstriker.junior.skills.SkillStore
import com.riglineaimstriker.junior.ui.components.InlineNotice
import com.riglineaimstriker.junior.ui.components.JuniorCompactShape
import com.riglineaimstriker.junior.ui.components.JuniorIcon
import com.riglineaimstriker.junior.ui.components.JuniorIcons
import com.riglineaimstriker.junior.ui.components.ScreenTopBar
import com.riglineaimstriker.junior.ui.components.Space
import com.riglineaimstriker.junior.ui.components.humanError
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val store: SkillStore,
    @ApplicationContext private val ctx: Context,
) : ViewModel() {

    val skills: StateFlow<List<Skill>> = store.skills

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun toggle(name: String) { _message.value = store.toggle(name) }

    fun add(name: String, text: String): Boolean {
        val err = store.add(name, "", text)
        _message.value = err
        return err == null
    }

    fun remove(name: String) { store.remove(name) }

    fun dismissMessage() { _message.value = null }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val limit = SkillStore.MAX_FILE_CHARS + 1
                    val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { r ->
                        val buf = CharArray(limit)
                        var n = 0
                        while (n < limit) {
                            val k = r.read(buf, n, limit - n)
                            if (k < 0) break
                            n += k
                        }
                        String(buf, 0, n)
                    } ?: return@runCatching "Не удалось открыть файл."
                    store.importText(text, displayName(uri))
                }.getOrElse { "Не удалось прочитать файл." }
            }
            _message.value = result
        }
    }

    private fun displayName(uri: Uri): String {
        val raw = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment ?: "навык"
        return raw.substringBeforeLast('.').take(SkillStore.MAX_NAME_CHARS).ifBlank { "навык" }
    }
}

/**
 * Навыки: включение/выключение, свой навык, импорт из .md. Текст навыков и промпты не меняются.
 * Список никогда не бывает пустым (есть встроенные навыки), поэтому пустого состояния нет;
 * ошибки (лимит включённых, неверный файл) приходят из SkillsViewModel.message.
 */
@Composable
fun SkillsScreen(onBack: () -> Unit, vm: SkillsViewModel = hiltViewModel()) {
    val skills by vm.skills.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Skill?>(null) }
    val active = skills.count { it.active }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importUri(uri)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScreenTopBar(
            title = "Навыки",
            subtitle = "Включено $active из ${SkillStore.MAX_ACTIVE}",
            onBack = onBack,
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.m),
        ) {
            item {
                Text(
                    "Навык — короткое правило, которое Junior соблюдает в каждом ответе. " +
                        "Например, отвечать кратко или писать комментарии по-русски. " +
                        "Одновременно можно включить не больше ${SkillStore.MAX_ACTIVE}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Space.m),
                )
            }
            message?.let { m ->
                item {
                    InlineNotice(
                        humanError(m, "Не удалось изменить навыки. Попробуйте ещё раз."), isError = true,
                        modifier = Modifier.padding(bottom = Space.m),
                        action = { TextButton(onClick = vm::dismissMessage) { Text("Понятно") } },
                    )
                }
            }
            items(skills, key = { it.name }) { s ->
                SkillRow(s, onToggle = { vm.toggle(s.name) }, onRemove = { removing = s })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(Space.l),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text("Свой навык")
            }
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text("Из файла .md")
            }
        }
    }

    if (showAdd) {
        AddSkillDialog(
            error = message,
            onDismiss = { showAdd = false; vm.dismissMessage() },
            onAdd = { name, text -> if (vm.add(name, text)) showAdd = false },
        )
    }
    removing?.let { s ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Удалить навык?") },
            text = { Text("Навык «${s.name}» будет удалён.") },
            confirmButton = {
                TextButton(onClick = { vm.remove(s.name); removing = null }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SkillRow(skill: Skill, onToggle: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = skill.active, role = Role.Switch, onValueChange = { onToggle() })
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(skill.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                skill.description.ifBlank { if (skill.builtin) "Встроенный навык" else "Свой навык" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!skill.builtin) {
            IconButton(onClick = onRemove) {
                JuniorIcon(JuniorIcons.Delete, contentDescription = "Удалить навык «${skill.name}»")
            }
        }
        Spacer(Modifier.width(Space.s))
        Switch(checked = skill.active, onCheckedChange = null)
    }
}

@Composable
private fun AddSkillDialog(error: String?, onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Свой навык") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(SkillStore.MAX_NAME_CHARS) },
                    label = { Text("Название") },
                    singleLine = true,
                    shape = JuniorCompactShape,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(SkillStore.MAX_FILE_CHARS) },
                    label = { Text("Правило для Junior") },
                    placeholder = { Text("Например: всегда добавляй пример использования") },
                    minLines = 4,
                    maxLines = 8,
                    shape = JuniorCompactShape,
                )
                if (error != null) {
                    Text(humanError(error, "Не удалось добавить навык."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, text) }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
