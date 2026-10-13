package com.riglineaimstriker.junior.ui.chat

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riglineaimstriker.junior.inference.EngineState
import com.riglineaimstriker.junior.ui.components.ActivityLine
import com.riglineaimstriker.junior.ui.components.chatStatusText
import com.riglineaimstriker.junior.ui.components.InlineNotice
import com.riglineaimstriker.junior.ui.components.JuniorCompactShape
import com.riglineaimstriker.junior.ui.components.JuniorIcon
import com.riglineaimstriker.junior.ui.components.JuniorIcons
import com.riglineaimstriker.junior.ui.components.JuniorShape
import com.riglineaimstriker.junior.ui.components.MOTION_MS
import com.riglineaimstriker.junior.ui.components.ScreenTopBar
import com.riglineaimstriker.junior.ui.components.Space
import com.riglineaimstriker.junior.ui.components.formatSize
import com.riglineaimstriker.junior.ui.components.humanError
import com.riglineaimstriker.junior.ui.components.rememberReducedMotion
import com.riglineaimstriker.junior.ui.theme.JuniorCodeFont
import com.riglineaimstriker.junior.ui.theme.LocalJuniorColors
import com.riglineaimstriker.junior.util.CodeLang
import com.riglineaimstriker.junior.util.FileExport
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenChats: () -> Unit,
    vm: ChatViewModel = hiltViewModel(),
) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val engine by vm.engineState.collectAsStateWithLifecycle()
    val generating by vm.generating.collectAsStateWithLifecycle()
    val modelName by vm.modelName.collectAsStateWithLifecycle()
    val hasModel by vm.hasModel.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val attachment by vm.attachment.collectAsStateWithLifecycle()
    val editingId by vm.editingId.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf(vm.initialPrompt) }
    var renaming by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val ready = engine is EngineState.Ready
    val ctx = LocalContext.current
    val canExport = messages.any { !it.isUser && it.text.isNotBlank() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.attach(uri)
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val said = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!said.isNullOrBlank()) input = if (input.isBlank()) said else input.trimEnd() + " " + said
        }
    }
    val startVoice: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите…")
        }
        val available = SpeechRecognizer.isRecognitionAvailable(ctx) ||
            intent.resolveActivity(ctx.packageManager) != null
        if (!available) {
            vm.showNotice("На телефоне нет распознавания речи. Установите или включите Google (голосовой ввод).")
        } else {
            try {
                voice.launch(intent)
            } catch (e: ActivityNotFoundException) {
                vm.showNotice("Голосовой ввод недоступен на этом устройстве.")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
    ) {
        ScreenTopBar(
            title = title,
            subtitle = "$modelName · без интернета",
            onBack = onBack,
            onTitleClick = { if (messages.isNotEmpty()) renaming = true },
            actions = {
                IconButton(onClick = { vm.newChat(); input = "" }) {
                    JuniorIcon(JuniorIcons.NewChat, contentDescription = "Новый чат")
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        JuniorIcon(JuniorIcons.More, contentDescription = "Ещё")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        MenuItem("Все чаты", JuniorIcons.Chat) { menu = false; onOpenChats() }
                        if (messages.isNotEmpty()) {
                            MenuItem("Переименовать", JuniorIcons.Edit) { menu = false; renaming = true }
                        }
                        if (canExport) {
                            MenuItem("Поделиться чатом", JuniorIcons.Share) {
                                menu = false
                                FileExport.share(ctx, "junior-chat.md", chatMarkdown(messages))
                            }
                        }
                        MenuItem("Модели", JuniorIcons.Download) { menu = false; onOpenModels() }
                    }
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) EmptyChat()
            else MessageList(
                messages = messages,
                status = status,
                generating = generating,
                onRegenerate = vm::regenerate,
                onEdit = { id -> vm.startEdit(id)?.let { input = it } },
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            notice?.let { n ->
                InlineNotice(humanError(n, "Не удалось выполнить действие."), isError = false, action = { TextButton(onClick = vm::dismissNotice) { Text("Понятно") } })
            }
            EngineBanner(engine, hasModel, onRetry = vm::reload, onPickModel = onOpenModels)
            AnimatedVisibility(
                visible = editingId != null,
                enter = fadeIn(tween(MOTION_MS)),
                exit = fadeOut(tween(MOTION_MS)),
            ) {
                EditingBanner(onCancel = { vm.cancelEdit(); input = "" })
            }
            attachment?.let { a -> AttachmentChip(a, onRemove = vm::removeAttachment) }
        }
        InputBar(
            value = input,
            onValueChange = { input = it },
            generating = generating,
            canSend = ready && (input.isNotBlank() || attachment != null),
            onSend = { if (vm.send(input)) input = "" },
            onStop = vm::stop,
            onAttach = { filePicker.launch(arrayOf("*/*")) },
            onVoice = startVoice,
        )
    }

    if (renaming) {
        RenameDialog(
            initial = title,
            onDismiss = { renaming = false },
            onSave = { vm.rename(it); renaming = false },
        )
    }
}

@Composable
private fun MenuItem(text: String, icon: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { JuniorIcon(icon, contentDescription = null, size = 20.dp) },
        onClick = onClick,
    )
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Название чата") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(80) },
                singleLine = true,
                label = { Text("Название") },
                shape = JuniorCompactShape,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun EmptyChat() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.xl),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text("Новый чат", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.size(Space.s))
        Text(
            "Спросите о коде: объяснить, найти ошибку, написать функцию или тесты. " +
                "Можно приложить файл кнопкой со скрепкой или продиктовать вопрос.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageList(
    messages: List<UiMessage>,
    status: String?,
    generating: Boolean,
    onRegenerate: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val reduced = rememberReducedMotion()
    val reversed = remember(messages) { messages.asReversed() }
    val lastId = messages.last().id
    LazyColumn(
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        items(reversed, key = { it.id }) { m ->
            // Явная короткая анимация (200 мс) вместо пружины по умолчанию; при отключённых анимациях — без неё.
            val anim = if (reduced) Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
            else Modifier.animateItem(
                fadeInSpec = tween(MOTION_MS),
                placementSpec = tween<IntOffset>(MOTION_MS),
                fadeOutSpec = tween(MOTION_MS),
            )
            Box(anim) {
                if (m.isUser) UserMessage(m, canEdit = !generating, onEdit = { onEdit(m.id) })
                else AssistantMessage(
                    m,
                    status = if (m.id == lastId && m.streaming) status else null,
                    canRegenerate = m.id == lastId && !generating,
                    onRegenerate = onRegenerate,
                )
            }
        }
    }
}

@Composable
private fun UserMessage(m: UiMessage, canEdit: Boolean, onEdit: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(Modifier.fillMaxWidth(0.85f), horizontalAlignment = Alignment.End) {
            m.attachment?.let { a -> AttachmentChip(a, onRemove = null) }
            if (m.text.isNotBlank()) {
                if (m.attachment != null) Spacer(Modifier.size(Space.xs))
                SelectionContainer {
                    Text(
                        m.text,
                        Modifier
                            .clip(JuniorShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = Space.l, vertical = Space.m),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    JuniorIcon(
                        JuniorIcons.More, contentDescription = "Действия с сообщением", size = 20.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (m.text.isNotBlank()) {
                        MenuItem("Копировать", JuniorIcons.Copy) {
                            menu = false
                            clipboard.setText(AnnotatedString(m.text))
                        }
                    }
                    if (canEdit) {
                        MenuItem("Изменить", JuniorIcons.Edit) { menu = false; onEdit() }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(a: Attachment, onRemove: (() -> Unit)?) {
    Row(
        Modifier
            .clip(JuniorCompactShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, JuniorCompactShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .heightIn(min = 48.dp)
            .padding(start = Space.m, end = if (onRemove != null) 0.dp else Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JuniorIcon(JuniorIcons.File, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Space.s))
        Column(Modifier.widthIn(max = 220.dp).padding(vertical = Space.xs)) {
            Text(
                a.name, style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${a.lang} · ${sizeLabel(a.sizeBytes)} · строк: ${a.content.lines().size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                JuniorIcon(JuniorIcons.Close, contentDescription = "Убрать файл «${a.name}»", size = 20.dp)
            }
        }
    }
}

private fun sizeLabel(bytes: Long): String = formatSize(bytes)

@Composable
private fun AssistantMessage(
    m: UiMessage,
    status: String?,
    canRegenerate: Boolean,
    onRegenerate: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (m.text.isNotEmpty()) MessageContent(m.text, m.streaming)
        if (m.streaming) {
            // Подпись берётся из реального состояния генерации (ChatViewModel.status).
            ActivityLine(chatStatusText(status), Modifier.padding(top = Space.s))
        }
        m.error?.let {
            Spacer(Modifier.size(Space.s))
            InlineNotice(
                humanError(it, "Не удалось получить ответ. Повторите запрос."), isError = true,
                action = if (canRegenerate) ({ TextButton(onClick = onRegenerate) { Text("Повторить") } }) else null,
            )
        }
        if (!m.streaming && m.text.isNotEmpty()) {
            Box {
                IconButton(onClick = { menu = true }) {
                    JuniorIcon(
                        JuniorIcons.More, contentDescription = "Действия с ответом", size = 20.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem("Копировать", JuniorIcons.Copy) {
                        menu = false
                        clipboard.setText(AnnotatedString(m.text))
                    }
                    MenuItem("Поделиться", JuniorIcons.Share) {
                        menu = false
                        FileExport.share(ctx, "junior-answer.md", m.text)
                    }
                    if (canRegenerate) {
                        MenuItem("Ответить заново", JuniorIcons.Repeat) { menu = false; onRegenerate() }
                    }
                }
            }
        }
    }
}

private sealed interface Seg {
    data class Prose(val text: String) : Seg
    data class Code(val lang: String, val code: String, val closed: Boolean) : Seg
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
        if (close < 0) { out += Seg.Code(lang, src.substring(nl + 1), closed = false); break }
        out += Seg.Code(lang, src.substring(nl + 1, close).trimEnd('\n'), closed = true)
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
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(s.substring(i + 2, e)) }
                i = e + 2
                continue
            }
        } else if (s[i] == '`') {
            val e = s.indexOf('`', i + 1)
            if (e > 0) {
                withStyle(SpanStyle(fontFamily = JuniorCodeFont, background = codeBg)) {
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
    val codeBg = LocalJuniorColors.current.codeBackground
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        segs.forEachIndexed { idx, seg ->
            when (seg) {
                is Seg.Prose -> {
                    val t = seg.text.trim('\n')
                    if (t.isNotBlank()) {
                        // Курсор не рисуем: идущую генерацию показывает строка состояния под ответом.
                        SelectionContainer {
                            Text(
                                inlineMarkdown(t, codeBg),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
                is Seg.Code -> CodeBlock(seg.lang, seg.code, writing = streaming && !seg.closed)
            }
        }
    }
}

@Composable
fun CodeBlock(lang: String, code: String, writing: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val extra = LocalJuniorColors.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = if (dark) SyntaxHighlighter.DarkPalette else SyntaxHighlighter.LightPalette
    val highlighted = remember(code, lang, dark) { SyntaxHighlighter.highlight(code, lang, palette) }
    var copied by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(1500); copied = false }
    }
    val label = CodeLang.normalize(lang).ifBlank { "код" }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(JuniorCompactShape)
            .border(1.dp, extra.codeBorder, JuniorCompactShape)
            .background(extra.codeBackground),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label + if (writing) " · пишется…" else "",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JuniorCodeFont),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Компактно: на узком экране и при крупном шрифте метка языка не вытесняет кнопки.
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(code))
                copied = true
            }) {
                JuniorIcon(
                    if (copied) JuniorIcons.Check else JuniorIcons.Copy,
                    contentDescription = if (copied) "Код скопирован" else "Копировать код",
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    JuniorIcon(JuniorIcons.More, contentDescription = "Ещё для кода", size = 20.dp)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem("Поделиться файлом", JuniorIcons.Share) {
                        menu = false
                        FileExport.share(ctx, "junior-code.${FileExport.extFor(lang)}", code)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(extra.codeBorder).heightIn(min = 1.dp, max = 1.dp))
        SelectionContainer {
            Text(
                highlighted,
                Modifier.horizontalScroll(rememberScrollState()).padding(Space.m),
                fontFamily = JuniorCodeFont,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun EditingBanner(onCancel: () -> Unit) {
    InlineNotice(
        "Правка сообщения: ответы после него будут заменены.",
        isError = false,
        action = { TextButton(onClick = onCancel) { Text("Отмена") } },
    )
}

@Composable
private fun EngineBanner(
    state: EngineState, hasModel: Boolean, onRetry: () -> Unit, onPickModel: () -> Unit,
) {
    when {
        state is EngineState.Loading -> ActivityLine("Загружаю модель…", Modifier.padding(vertical = Space.xs))
        state is EngineState.Failed -> InlineNotice(
            humanError(state.message, "Не удалось загрузить модель."), isError = true,
            action = { TextButton(onClick = onRetry) { Text("Повторить") } },
        )
        !hasModel -> InlineNotice(
            "Модель не выбрана.", isError = false,
            action = { TextButton(onClick = onPickModel) { Text("Выбрать") } },
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
    onAttach: () -> Unit,
    onVoice: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(Space.m)
            .clip(JuniorShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, JuniorShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Space.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        IconButton(onClick = onAttach, enabled = !generating) {
            JuniorIcon(
                JuniorIcons.Attach, contentDescription = "Приложить файл с кодом",
                tint = if (generating) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .padding(vertical = Space.m, horizontal = Space.xs)
                .semantics { contentDescription = "Сообщение для Junior" },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            // В альбомной ориентации с клавиатурой поле не должно вытеснять ленту.
            maxLines = if (LocalConfiguration.current.screenHeightDp < 480) 3 else 6,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            "Сообщение",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        if (!generating && value.isBlank()) {
            IconButton(onClick = onVoice) {
                JuniorIcon(JuniorIcons.Mic, contentDescription = "Голосовой ввод")
            }
        }
        if (generating) {
            FilledIconButton(onClick = onStop) {
                JuniorIcon(JuniorIcons.Stop, contentDescription = "Остановить", tint = MaterialTheme.colorScheme.onPrimary)
            }
        } else {
            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                colors = IconButtonDefaults.filledIconButtonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                JuniorIcon(
                    JuniorIcons.Send, contentDescription = "Отправить",
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun chatMarkdown(list: List<UiMessage>): String = buildString {
    appendLine("# Чат с Junior")
    appendLine()
    list.forEach { m ->
        if (m.text.isBlank() && m.attachment == null) return@forEach
        appendLine(if (m.isUser) "## Вы" else "## Junior")
        appendLine()
        m.attachment?.let { appendLine("Файл: ${it.name}"); appendLine() }
        if (m.text.isNotBlank()) appendLine(m.text.trim())
        appendLine()
    }
}
