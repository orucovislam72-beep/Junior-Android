package com.riglineaimstriker.junior.ui.skills

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riglineaimstriker.junior.skills.Skill
import com.riglineaimstriker.junior.skills.SkillStore
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

@Composable
fun SkillsScreen(onBack: () -> Unit, vm: SkillsViewModel = hiltViewModel()) {
    val skills by vm.skills.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importUri(uri)
    }

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
            Text("Навыки Rigline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            "Навык — короткая инструкция, которая добавляется к вашему запросу. " +
                "Включено можно не больше ${SkillStore.MAX_ACTIVE}. Выключенные ничего не стоят.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        message?.let { m ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    m,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::dismissMessage) { Text("Ок") }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(skills, key = { it.name }) { s ->
                SkillCard(s, onToggle = { vm.toggle(s.name) }, onRemove = { vm.remove(s.name) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { showAdd = true },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Свой навык") }
            OutlinedButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Из файла .md") }
        }
        Spacer(Modifier.height(10.dp))
    }

    if (showAdd) {
        AddSkillDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, text -> if (vm.add(name, text)) showAdd = false },
        )
    }
}

@Composable
private fun SkillCard(skill: Skill, onToggle: () -> Unit, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(skill.name, fontWeight = FontWeight.SemiBold)
                    if (skill.description.isNotBlank()) {
                        Text(
                            skill.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = skill.active, onCheckedChange = { onToggle() })
            }
            if (!skill.builtin) {
                TextButton(onClick = onRemove) { Text("Удалить") }
            }
        }
    }
}

@Composable
private fun AddSkillDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Свой навык") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(SkillStore.MAX_NAME_CHARS) },
                    label = { Text("Название") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(SkillStore.MAX_FILE_CHARS) },
                    label = { Text("Инструкция") },
                    minLines = 4,
                    maxLines = 8,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, text) }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
