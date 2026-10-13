package com.riglineaimstriker.junior.ui.setup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riglineaimstriker.junior.model.DownloadState
import com.riglineaimstriker.junior.model.ModelProfile
import com.riglineaimstriker.junior.model.Profiles
import com.riglineaimstriker.junior.R
import com.riglineaimstriker.junior.ui.components.Illustration
import com.riglineaimstriker.junior.ui.components.InlineNotice
import com.riglineaimstriker.junior.ui.components.JuniorIcon
import com.riglineaimstriker.junior.ui.components.JuniorIcons
import com.riglineaimstriker.junior.ui.components.ScreenTopBar
import com.riglineaimstriker.junior.ui.components.Space
import com.riglineaimstriker.junior.ui.components.formatGb
import com.riglineaimstriker.junior.ui.components.humanError
import com.riglineaimstriker.junior.ui.components.rememberReducedMotion

/** Гигабайты по-русски с неразрывным пробелом: «1,2 ГБ». */
private fun gb(bytes: Double) = formatGb(bytes)

@Composable
fun SetupScreen(onDone: () -> Unit, onBack: (() -> Unit)? = null, vm: SetupViewModel = hiltViewModel()) {
    val models by vm.items.collectAsStateWithLifecycle()
    val selected by vm.selectedId.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<ModelProfile?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.let(vm::download)
        pending = null
    }
    val customReady by vm.customReady.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }
    LaunchedEffect(Unit) { vm.imported.collect { onDone() } }
    val startDownload: (ModelProfile) -> Unit = { p ->
        val needAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needAsk) {
            pending = p
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else vm.download(p)
    }

    // Не больше одного неопределённого (бесконечного) индикатора на экране:
    // он достаётся первой загрузке/импорту с неизвестным размером, остальные ждут со статичной подписью.
    val spinnerOwner: String? = models.firstOrNull { val d = it.download; d is DownloadState.Running && d.total <= 0 }
        ?.profile?.id
        ?: (importState as? ImportState.Running)?.takeIf { it.total <= 0 }?.let { Profiles.Custom.id }
    // Одно заметное главное действие: у выбранной модели, иначе у рекомендованной.
    val primaryId = selected ?: models.firstOrNull { it.recommended }?.profile?.id

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScreenTopBar(title = "Модель", onBack = onBack, subtitle = "Модель отвечает на вопросы прямо на телефоне")
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = Space.l),
        ) {
            item {
                Column(Modifier.padding(horizontal = Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Illustration(R.drawable.il_model)
                    StepLine(1, "Выберите модель. Подходящая для вашего телефона отмечена.")
                    StepLine(2, "Скачайте её один раз — понадобится интернет и свободное место.")
                    StepLine(3, "Дальше модель отвечает без интернета.")
                }
                Spacer(Modifier.height(Space.l))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            items(models, key = { it.profile.id }) { ui ->
                ModelSection(
                    ui = ui,
                    isSelected = selected == ui.profile.id,
                    prominent = ui.profile.id == primaryId,
                    spinner = spinnerOwner == ui.profile.id,
                    onDownload = { startDownload(ui.profile) },
                    onCancel = { vm.cancel(ui.profile) },
                    onDelete = { vm.delete(ui.profile) },
                    onUse = { vm.use(ui.profile); onDone() },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item {
                CustomModelSection(
                    ready = customReady,
                    state = importState,
                    isSelected = selected == Profiles.Custom.id,
                    spinner = spinnerOwner == Profiles.Custom.id,
                    onPick = { pickFile.launch(arrayOf("*/*")) },
                    onUse = { vm.use(Profiles.Custom); onDone() },
                    onDelete = vm::deleteCustom,
                )
            }
        }
    }
}

@Composable
private fun StepLine(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "$n.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(24.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Плоский раздел модели между разделителями. Поверхность (мягкий зелёный фон)
 * появляется только у выбранной модели — это и есть выбранное состояние.
 */
@Composable
private fun Section(selected: Boolean, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .padding(horizontal = Space.l, vertical = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) { content() }
}

/** Реальный прогресс в ГБ; если размер ещё неизвестен — статичная подпись и (только у одного) бесконечная полоса. */
@Composable
private fun Progress(done: Long, total: Long, waitingText: String, spinner: Boolean) {
    if (total > 0) {
        LinearProgressIndicator(
            progress = { (done.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${gb(done.toDouble())} из ${gb(total.toDouble())}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        if (spinner && !rememberReducedMotion()) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(waitingText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Главное действие раздела: заполненная кнопка только у одного раздела, у остальных — контурная. */
@Composable
private fun MainAction(prominent: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    if (prominent) {
        Button(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { content() }
    } else {
        OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { content() }
    }
}

@Composable
private fun ModelSection(
    ui: ProfileUi,
    isSelected: Boolean,
    prominent: Boolean,
    spinner: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onUse: () -> Unit,
) {
    val p = ui.profile
    Section(selected = isSelected) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                gb(p.sizeGb * 1e9),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isSelected) {
            Text("Сейчас используется", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        } else if (ui.recommended) {
            Text("Подходит вашему телефону", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text(p.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            !ui.runnable -> Text(
                "Телефону не хватит памяти: нужно от ${p.minRamGb.toInt()}\u00A0ГБ оперативной памяти.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ui.download is DownloadState.Done -> {
                MainAction(prominent, onUse) { Text(if (isSelected) "Продолжить" else "Использовать") }
                TextButton(onDelete) { Text("Удалить модель") }
            }
            ui.download is DownloadState.Running -> {
                val d = ui.download as DownloadState.Running
                Progress(d.done, d.total, "Ждём сеть…", spinner)
                TextButton(onCancel) { Text("Отменить загрузку") }
            }
            ui.download is DownloadState.Failed -> {
                InlineNotice(
                    humanError((ui.download as DownloadState.Failed).message, "Загрузка не удалась. Проверьте интернет."),
                    isError = true,
                )
                MainAction(prominent, onDownload) { Text("Повторить") }
            }
            else -> MainAction(prominent, onDownload) {
                JuniorIcon(JuniorIcons.Download, contentDescription = null, size = 20.dp)
                Spacer(Modifier.width(Space.s))
                Text("Скачать")
            }
        }
    }
}

@Composable
private fun CustomModelSection(
    ready: Boolean,
    state: ImportState,
    isSelected: Boolean,
    spinner: Boolean,
    onPick: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    Section(selected = isSelected) {
        Text("Своя модель из файла", style = MaterialTheme.typography.titleMedium)
        Text(
            "Если модель в формате GGUF уже лежит на телефоне, её можно подключить из файла.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { more = !more }) {
            Text(if (more) "Скрыть подробности" else "Подробнее")
            Spacer(Modifier.width(Space.xs))
            JuniorIcon(
                if (more) JuniorIcons.ChevronDown else JuniorIcons.ChevronRight,
                contentDescription = null, size = 20.dp,
            )
        }
        if (more) {
            Text(
                "Подойдёт ли конкретная модель GGUF, зависит от самой модели и от телефона: " +
                    "большая модель может не запуститься из-за нехватки памяти. Файл копируется в память приложения, " +
                    "поэтому на время копирования нужно место ещё под одну копию.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (state) {
            is ImportState.Running -> Progress(state.done, state.total, "Готовим файл…", spinner)
            else -> {
                if (state is ImportState.Failed) {
                    InlineNotice(humanError(state.message, "Не удалось подключить файл модели."), isError = true)
                }
                if (ready) {
                    // Действия столбиком: на 360 dp и при крупном шрифте три кнопки в ряд не помещаются.
                    MainAction(isSelected, onUse) { Text(if (isSelected) "Продолжить" else "Использовать") }
                    TextButton(onPick) { Text("Заменить файл") }
                    TextButton(onDelete) { Text("Удалить модель") }
                } else {
                    OutlinedButton(onPick, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        JuniorIcon(JuniorIcons.File, contentDescription = null, size = 20.dp)
                        Spacer(Modifier.width(Space.s))
                        Text("Выбрать файл .gguf")
                    }
                }
            }
        }
    }
}
