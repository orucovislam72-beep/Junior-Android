package com.riglineaimstriker.junior.agent

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riglineaimstriker.junior.R
import com.riglineaimstriker.junior.inference.EngineState
import com.riglineaimstriker.junior.ui.components.ActivityLine
import com.riglineaimstriker.junior.ui.components.agentStatusText
import com.riglineaimstriker.junior.ui.components.EmptyState
import com.riglineaimstriker.junior.ui.components.InlineNotice
import com.riglineaimstriker.junior.ui.components.JuniorCompactShape
import com.riglineaimstriker.junior.ui.components.JuniorIcon
import com.riglineaimstriker.junior.ui.components.JuniorIcons
import com.riglineaimstriker.junior.ui.components.JuniorShape
import com.riglineaimstriker.junior.ui.components.ScreenTopBar
import com.riglineaimstriker.junior.ui.components.SectionTitle
import com.riglineaimstriker.junior.ui.components.Space
import com.riglineaimstriker.junior.ui.components.humanError
import com.riglineaimstriker.junior.ui.theme.JuniorCodeFont
import com.riglineaimstriker.junior.ui.theme.LocalJuniorColors

@Composable
fun AgentScreen(onBack: () -> Unit, onOpenModels: () -> Unit, vm: AgentViewModel = hiltViewModel()) {
    val ws by vm.workspace.collectAsStateWithLifecycle()
    val steps by vm.steps.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val autoApprove by vm.autoApprove.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val engine by vm.engineState.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.useTree(uri)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        ScreenTopBar(
            title = "Агент для проекта",
            subtitle = "Rigline Code · работает с файлами на телефоне",
            onBack = onBack,
            actions = {
                if (steps.isNotEmpty() && !running) {
                    IconButton(onClick = vm::clear) {
                        JuniorIcon(JuniorIcons.Delete, contentDescription = "Очистить ленту действий")
                    }
                }
            },
        )

        val current = ws
        if (current == null) {
            FolderChooser(
                onPickFolder = { treePicker.launch(null) },
                onInternal = vm::useInternal,
                modifier = Modifier.weight(1f),
            )
        } else {
            // Папка и переключатель прокручиваются вместе с лентой: в альбомной ориентации
            // с клавиатурой они не отнимают место у ленты и поля ввода.
            AgentFeed(
                title = current.title,
                running = running,
                autoApprove = autoApprove,
                steps = steps,
                onChangeFolder = vm::changeFolder,
                onAutoApprove = vm::setAutoApprove,
                onPick = { input = it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            notice?.let { n ->
                // Текст уведомления может содержать системное сообщение об ошибке — показываем только понятную часть.
                val text = if (n.startsWith("Не удалось открыть папку")) "Не удалось открыть папку. Выберите её ещё раз." else n
                InlineNotice(text, isError = true, action = { TextButton(onClick = vm::dismissNotice) { Text("Понятно") } })
            }
            when (val e = engine) {
                is EngineState.Loading -> ActivityLine("Загружаю модель…")
                is EngineState.Failed -> InlineNotice(
                    humanError(e.message, "Не удалось загрузить модель."), isError = true,
                    action = { TextButton(onClick = onOpenModels) { Text("Модели") } },
                )
                EngineState.Unloaded -> InlineNotice(
                    "Модель не загружена.", isError = false,
                    action = { TextButton(onClick = onOpenModels) { Text("Выбрать") } },
                )
                EngineState.Ready -> status?.let { s ->
                    // Статус из AgentViewModel; технические числа после « · » не показываем.
                    ActivityLine(agentStatusText(s))
                }
            }
        }
        if (current != null) {
            TaskInput(
                value = input,
                onValueChange = { input = it },
                running = running,
                canRun = engine is EngineState.Ready && input.isNotBlank(),
                onRun = { vm.run(input); input = "" },
                onStop = vm::stop,
            )
        }
    }

    pending?.let { p ->
        WriteConfirmDialog(
            p,
            onApprove = { vm.decide(true) },
            onApproveAll = { vm.decide(true, allowAll = true) },
            onReject = { vm.decide(false) },
        )
    }
}

@Composable
private fun FolderChooser(onPickFolder: () -> Unit, onInternal: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        EmptyState(
            illustration = R.drawable.il_no_folder,
            title = "Выберите папку проекта",
            text = "Junior посмотрит файлы, поищет по коду и предложит правки. " +
                "Каждое изменение файла вы подтверждаете сами.",
            action = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Button(onClick = onPickFolder, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        JuniorIcon(JuniorIcons.Folder, contentDescription = null, size = 20.dp,
                            tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(Space.s))
                        Text("Выбрать папку на телефоне")
                    }
                    OutlinedButton(onClick = onInternal, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Использовать папку внутри Junior")
                    }
                    Text(
                        "Внутренняя папка не требует разрешений — удобно, чтобы попробовать.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@Composable
private fun WorkspaceBar(title: String, enabled: Boolean, onChange: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Space.l, end = Space.xs, top = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JuniorIcon(JuniorIcons.Folder, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Space.s))
        Column(Modifier.weight(1f)) {
            Text("Папка проекта", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onChange, enabled = enabled) { Text("Сменить") }
    }
}

@Composable
private fun AutoApproveRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onChange(!checked) }
            .padding(horizontal = Space.l, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Разрешать всё в этой сессии", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (checked) "Файлы записываются без вопросов, пока вы на этом экране."
                else "Перед каждой записью файла Junior спросит разрешения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Space.m))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AgentEmpty(onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        SectionTitle("Что сделать в проекте?")
        Text(
            "Опишите задачу своими словами. Junior покажет каждый шаг: какие файлы смотрит, что ищет и что хочет изменить.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Например:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.s),
        )
        listOf(
            "Посмотри файлы проекта и кратко опиши, что это за проект",
            "Найди, где используется функция main, и объясни её",
            "Создай файл README.md с описанием проекта",
        ).forEach { s ->
            Text(
                "«$s»",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(JuniorCompactShape)
                    .clickable { onPick(s) }
                    .padding(horizontal = Space.s, vertical = Space.m),
            )
        }
    }
}

private const val FEED_HEADER_ITEMS = 3

@Composable
private fun AgentFeed(
    title: String,
    running: Boolean,
    autoApprove: Boolean,
    steps: List<AgentStep>,
    onChangeFolder: () -> Unit,
    onAutoApprove: (Boolean) -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier,
) {
    val state = rememberLazyListState()
    LaunchedEffect(steps.size, steps.lastOrNull()?.detail?.length) {
        if (steps.isNotEmpty()) state.scrollToItem(FEED_HEADER_ITEMS + steps.lastIndex)
    }
    LazyColumn(modifier, state = state, contentPadding = PaddingValues(bottom = Space.m)) {
        item { WorkspaceBar(title, enabled = !running, onChange = onChangeFolder) }
        item { AutoApproveRow(autoApprove, onChange = onAutoApprove) }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        if (steps.isEmpty()) {
            item { AgentEmpty(onPick) }
        } else {
            items(steps, key = { it.id }) { s ->
                Box(Modifier.padding(horizontal = Space.l)) { StepItem(s) }
            }
        }
    }
}

/** Человеческое название действия по инструменту (сырой синтаксис инструментов не показываем). */
private fun actionLabel(tool: String?): String = when (tool) {
    "list_files" -> "Смотрю файлы"
    "read_file" -> "Читаю файл"
    "write_file" -> "Пишу файл"
    "create_dir" -> "Создаю папку"
    "search" -> "Ищу в проекте"
    else -> "Неизвестное действие"
}

private fun actionIcon(tool: String?): Int = when (tool) {
    "list_files", "create_dir" -> JuniorIcons.Folder
    "read_file" -> JuniorIcons.File
    "write_file" -> JuniorIcons.Edit
    "search" -> JuniorIcons.Search
    else -> JuniorIcons.Warning
}

/**
 * Лента действий — хронология с линией слева, без карточек. Поверхность есть только у диффа
 * (это код) и у ошибок; запись файла подтверждается в отдельном диалоге.
 */
@Composable
private fun StepItem(s: AgentStep) {
    when (s.kind) {
        StepKind.Task -> Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            Text("Задача", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(s.detail, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        StepKind.Answer -> Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            if (s.status == StepStatus.Running) {
                // Пока модель пишет вызов инструмента, показываем только «Думаю…», без сырого текста.
                val visible = s.detail.takeIf { it.isNotBlank() && !it.contains("<tool") }
                Text(
                    "Думаю…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (visible != null) {
                    Text(visible, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.size(Space.m))
                Text("Итог", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(s.detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        StepKind.Note -> InlineNotice(
            s.title + if (s.detail.isNotBlank()) ": ${humanError(s.detail, "техническая ошибка")}" else "",
            isError = s.title.startsWith("Ошибка") || s.title.startsWith("Достигнут"),
            modifier = Modifier.padding(vertical = Space.s),
        )
        StepKind.Tool -> ToolItem(s)
    }
}

@Composable
private fun ToolItem(s: AgentStep) {
    var expanded by remember(s.id) { mutableStateOf(false) }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .drawBehind {
                // Вертикальная линия хронологии под значком.
                val x = 12.dp.toPx()
                drawLine(lineColor, Offset(x, 36.dp.toPx()), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
            .padding(vertical = Space.s),
    ) {
        Box(Modifier.size(24.dp).padding(top = 2.dp), contentAlignment = Alignment.Center) {
            JuniorIcon(actionIcon(s.tool), contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(actionLabel(s.tool), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                StatusMark(s.status)
            }
            if (s.tool == "search") {
                Text(s.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.path?.takeIf { it.isNotBlank() && it != "." }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JuniorCodeFont),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (s.detail.isNotBlank()) {
                val detail = if (s.status == StepStatus.Failed) humanError(s.detail, "Не удалось выполнить действие.") else s.detail
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            s.diff?.let { d -> DiffView(d, maxLines = if (expanded) 400 else 12) }
            if (!expanded && (s.detail.lines().size > 3 || (s.diff?.size ?: 0) > 12)) {
                Text(
                    "Нажмите, чтобы показать полностью",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StatusMark(status: StepStatus) {
    val extra = LocalJuniorColors.current
    when (status) {
        StepStatus.Running -> Text("выполняется…", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        StepStatus.Waiting -> Text("ждёт вашего решения", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary)
        StepStatus.Done -> JuniorIcon(JuniorIcons.Check, contentDescription = "Готово", size = 20.dp, tint = extra.success)
        StepStatus.Failed -> JuniorIcon(JuniorIcons.Warning, contentDescription = "Ошибка", size = 20.dp,
            tint = MaterialTheme.colorScheme.error)
        StepStatus.Rejected -> JuniorIcon(JuniorIcons.Close, contentDescription = "Отклонено", size = 20.dp,
            tint = MaterialTheme.colorScheme.error)
    }
}

/** Построчный дифф: зелёные — добавленные строки, красные — удалённые. Знаки +/− дублируют цвет. */
@Composable
private fun DiffView(lines: List<DiffLine>, maxLines: Int) {
    val extra = LocalJuniorColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(JuniorCompactShape)
            .border(1.dp, extra.codeBorder, JuniorCompactShape)
            .background(extra.codeBackground)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = Space.s),
    ) {
        lines.take(maxLines).forEach { l ->
            val (bg, fg, mark) = when (l.kind) {
                '+' -> Triple(extra.successContainer, extra.onSuccessContainer, "+ ")
                '-' -> Triple(extra.errorContainer, extra.onErrorContainer, "− ")
                '…' -> Triple(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant, "… ")
                else -> Triple(Color.Transparent, MaterialTheme.colorScheme.onSurface, "  ")
            }
            Text(
                mark + l.text,
                Modifier.background(bg).padding(horizontal = Space.s),
                fontFamily = JuniorCodeFont,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = fg,
                softWrap = false,
            )
        }
        if (lines.size > maxLines) {
            Text(
                "ещё строк: ${lines.size - maxLines}",
                Modifier.padding(horizontal = Space.s),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WriteConfirmDialog(
    p: PendingWrite,
    onApprove: () -> Unit,
    onApproveAll: () -> Unit,
    onReject: () -> Unit,
) {
    val name = p.path.substringAfterLast('/')
    val maxDiff = (LocalConfiguration.current.screenHeightDp * 0.4f).dp
    AlertDialog(
        onDismissRequest = {},
        icon = { JuniorIcon(JuniorIcons.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (p.isNew) "Создать файл $name?" else "Изменить файл $name?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text(
                    p.path,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JuniorCodeFont),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (p.isNew) "Новый файл, строк: ${p.lines}." else "Ниже — что изменится. Строк в новой версии: ${p.lines}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Box(Modifier.heightIn(max = maxDiff).verticalScroll(rememberScrollState())) {
                    DiffView(p.diff, maxLines = 400)
                }
                TextButton(onClick = onApproveAll, modifier = Modifier.fillMaxWidth()) {
                    Text("Применить и разрешать всё в этой сессии", fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = { Button(onClick = onApprove) { Text("Применить") } },
        dismissButton = { TextButton(onClick = onReject) { Text("Не сейчас") } },
    )
}

@Composable
private fun TaskInput(
    value: String,
    onValueChange: (String) -> Unit,
    running: Boolean,
    canRun: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(Space.m)
            .clip(JuniorShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, JuniorShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = Space.l, end = Space.xs, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .padding(vertical = Space.m)
                .semantics { contentDescription = "Задача для агента" },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = if (LocalConfiguration.current.screenHeightDp < 480) 3 else 5,
            enabled = !running,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            if (running) "Junior работает…" else "Опишите задачу",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.size(Space.xs))
        if (running) {
            FilledIconButton(onClick = onStop) {
                JuniorIcon(JuniorIcons.Stop, contentDescription = "Остановить", tint = MaterialTheme.colorScheme.onPrimary)
            }
        } else {
            FilledIconButton(
                onClick = onRun,
                enabled = canRun,
                colors = IconButtonDefaults.filledIconButtonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                JuniorIcon(
                    JuniorIcons.Send, contentDescription = "Запустить",
                    tint = if (canRun) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
