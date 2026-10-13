package com.riglineaimstriker.junior.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riglineaimstriker.junior.inference.EngineState
import com.riglineaimstriker.junior.ui.components.JuniorAvatar
import com.riglineaimstriker.junior.util.FileExport

@Composable
fun ChatScreen(onBack: () -> Unit, onOpenModels: () -> Unit, vm: ChatViewModel = hiltViewModel()) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val engine by vm.engineState.collectAsStateWithLifecycle()
    val generating by vm.generating.collectAsStateWithLifecycle()
    val modelName by vm.modelName.collectAsStateWithLifecycle()
    val hasModel by vm.hasModel.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf(vm.initialPrompt) }
    val ready = engine is EngineState.Ready
    val ctx = LocalContext.current

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
    ) {
        TopBar(
            onBack = onBack,
            onNew = vm::newChat,
            onExport = { FileExport.share(ctx, "junior-chat.md", chatMarkdown(messages)) },
            canExport = messages.any { !it.isUser && it.text.isNotBlank() },
            onSettings = onOpenModels,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) EmptyState(onPick = { input = it })
            else MessageList(messages, onRegenerate = vm::regenerate)
        }
        StatusBanner(engine, hasModel, onRetry = vm::reload, onPickModel = onOpenModels)
        ModelChip(modelName)
        InputBar(
            value = input,
            onValueChange = { input = it },
            generating = generating,
            canSend = ready && input.isNotBlank(),
            onSend = { vm.send(input); input = "" },
            onStop = vm::stop,
        )
    }
}

@Composable
private fun TopBar(
    onBack: () -> Unit,
    onNew: () -> Unit,
    onExport: () -> Unit,
    canExport: Boolean,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад в меню") }
        IconButton(onNew) { Icon(Icons.Default.Add, contentDescription = "Новый чат") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Junior", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF34C759)))
                Text(
                    "  Работает офлайн",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onExport, enabled = canExport) { Icon(Icons.Default.Share, contentDescription = "Отправить чат файлом") }
        IconButton(onSettings) { Icon(Icons.Default.Settings, contentDescription = "Модели и настройки") }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
}

@Composable
private fun EmptyState(onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        JuniorAvatar(size = 56.dp)
        Spacer(Modifier.height(16.dp))
        Text("Чем помочь?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Junior работает на вашем устройстве",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        val rows = listOf(
            listOf("Объясни код" to "Объясни, как работает этот код:\n", "Найди баг" to "Найди ошибку в этом коде:\n"),
            listOf("Оптимизируй" to "Оптимизируй этот код:\n", "Напиши тесты" to "Напиши тесты для этого кода:\n"),
        )
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, prompt) ->
                    SuggestionChip(label, Modifier.weight(1f)) { onPick(prompt) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SuggestionChip(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MessageList(messages: List<UiMessage>, onRegenerate: () -> Unit) {
    val reversed = remember(messages) { messages.asReversed() }
    val lastId = messages.last().id
    LazyColumn(
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(reversed, key = { it.id }) { m ->
            Box(Modifier.animateItem()) {
                if (m.isUser) UserBubble(m.text)
                else AssistantMessage(m, isLast = m.id == lastId, onRegenerate = onRegenerate)
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text,
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun AssistantMessage(m: UiMessage, isLast: Boolean, onRegenerate: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JuniorAvatar(26.dp)
            Text("  Junior", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        if (m.text.isEmpty() && m.streaming) TypingDots()
        else SelectionContainer { MessageContent(m.text, m.streaming) }
        m.error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (!m.streaming && m.text.isNotEmpty()) {
            Row(Modifier.padding(top = 4.dp)) {
                TextButton({ clipboard.setText(AnnotatedString(m.text)) }) {
                    Text("Копировать", style = MaterialTheme.typography.labelMedium)
                }
                TextButton({ FileExport.share(ctx, "junior-answer.md", m.text) }) {
                    Text("Файл", style = MaterialTheme.typography.labelMedium)
                }
                if (isLast) TextButton(onRegenerate) {
                    Text("Повторить", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private sealed interface Seg {
    data class Prose(val text: String) : Seg
    data class Code(val lang: String, val code: String) : Seg
}

private fun parseSegments(src: String): List<Seg> {
    val out = mutableListOf<Seg>()
    var i = 0
    while (i < src.length) {
        val open = src.indexOf("```", i)
        if (open < 0) { out += Seg.Prose(src.substring(i)); break }
        if (open > i) out += Seg.Prose(src.substring(i, open))
        val nl = src.indexOf('\n', open + 3)
        if (nl < 0) break
        val lang = src.substring(open + 3, nl).trim()
        val close = src.indexOf("```", nl + 1)
        if (close < 0) { out += Seg.Code(lang, src.substring(nl + 1)); break }
        out += Seg.Code(lang, src.substring(nl + 1, close).trimEnd('\n'))
        i = close + 3
    }
    return out
}

private fun inlineMarkdown(s: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        if (s.startsWith("**", i)) {
            val e = s.indexOf("**", i + 2)
            if (e > 0) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, e)) }
                i = e + 2
                continue
            }
        } else if (s[i] == '`') {
            val e = s.indexOf('`', i + 1)
            if (e > 0) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                    append(s.substring(i + 1, e))
                }
                i = e + 1
                continue
            }
        }
        append(s[i])
        i++
    }
}

@Composable
private fun MessageContent(text: String, streaming: Boolean) {
    val segs = remember(text) { parseSegments(text) }
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        segs.forEachIndexed { idx, seg ->
            when (seg) {
                is Seg.Prose -> {
                    val t = seg.text.trim('\n')
                    if (t.isNotBlank()) {
                        val cursor = if (streaming && idx == segs.lastIndex) " ▍" else ""
                        Text(inlineMarkdown(t + cursor, codeBg), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                is Seg.Code -> CodeBlock(seg.lang, seg.code)
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    lang.ifBlank { "код" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton({ FileExport.share(ctx, "junior-code.${FileExport.extFor(lang)}", code) }) {
                        Text("Файл", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton({ clipboard.setText(AnnotatedString(code)) }) {
                        Text("Копировать", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            Text(
                code,
                Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse, StartOffset(i * 150)),
                label = "dot$i",
            )
            Box(
                Modifier.size(7.dp).alpha(a)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
            )
        }
    }
}

@Composable
private fun StatusBanner(
    state: EngineState, hasModel: Boolean, onRetry: () -> Unit, onPickModel: () -> Unit,
) {
    when {
        state is EngineState.Loading -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "Загружаю модель…",
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state is EngineState.Failed -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.message, Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            )
            TextButton(onRetry) { Text("Повторить") }
        }
        !hasModel -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Модель не выбрана", Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onPickModel) { Text("Выбрать") }
        }
    }
}

@Composable
private fun ModelChip(name: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.Center) {
        Text(
            name,
            Modifier
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    generating: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            "Спросите Junior…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        val enabled = generating || canSend
        Box(
            Modifier
                .size(40.dp)
                .alpha(if (enabled) 1f else 0.4f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(enabled = enabled) { if (generating) onStop() else onSend() },
            contentAlignment = Alignment.Center,
        ) {
            if (generating) {
                Box(Modifier.size(14.dp).background(Color.White, RoundedCornerShape(3.dp)))
            } else {
                ArrowUp(Color.White)
            }
        }
    }
}

@Composable
private fun ArrowUp(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val sw = 2.2.dp.toPx()
        drawLine(color, Offset(w / 2, h * 0.9f), Offset(w / 2, h * 0.1f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.15f, h * 0.45f), Offset(w / 2, h * 0.08f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.85f, h * 0.45f), Offset(w / 2, h * 0.08f), sw, StrokeCap.Round)
    }
}

private fun chatMarkdown(list: List<UiMessage>): String = buildString {
    appendLine("# Чат с Junior")
    appendLine()
    list.forEach { m ->
        if (m.text.isBlank()) return@forEach
        appendLine(if (m.isUser) "## Вы" else "## Junior")
        appendLine()
        appendLine(m.text.trim())
        appendLine()
    }
}
