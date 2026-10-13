package com.riglineaimstriker.junior.ui.setup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riglineaimstriker.junior.model.DownloadState
import com.riglineaimstriker.junior.model.ModelProfile
import com.riglineaimstriker.junior.ui.components.JuniorAvatar
import java.util.Locale

private fun gb(bytes: Double) = String.format(Locale.getDefault(), "%.1f", bytes / 1e9)

@Composable
fun SetupScreen(onDone: () -> Unit, vm: SetupViewModel = hiltViewModel()) {
    val models by vm.items.collectAsStateWithLifecycle()
    val selected by vm.selectedId.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<ModelProfile?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.let(vm::download)
        pending = null
    }
    val startDownload: (ModelProfile) -> Unit = { p ->
        val needAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needAsk) {
            pending = p
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else vm.download(p)
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                JuniorAvatar(44.dp)
                Spacer(Modifier.height(16.dp))
                Text("Выберите модель", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Модель скачивается один раз и дальше работает без интернета.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        items(models, key = { it.profile.id }) { ui ->
            ModelCard(
                ui = ui,
                isSelected = selected == ui.profile.id,
                onDownload = { startDownload(ui.profile) },
                onCancel = { vm.cancel(ui.profile) },
                onDelete = { vm.delete(ui.profile) },
                onUse = { vm.use(ui.profile); onDone() },
            )
        }
    }
}

@Composable
private fun ModelCard(
    ui: ProfileUi,
    isSelected: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onUse: () -> Unit,
) {
    val p = ui.profile
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${String.format(Locale.getDefault(), "%.1f", p.sizeGb)} ГБ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                p.description + if (ui.recommended) " Рекомендуем для вашего телефона." else "",
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            when {
                !ui.runnable -> Text(
                    "Не хватает памяти: нужно от ${p.minRamGb.toInt()} ГБ ОЗУ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ui.download is DownloadState.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onUse) { Text(if (isSelected) "Продолжить" else "Использовать") }
                    TextButton(onDelete) { Text("Удалить") }
                }
                ui.download is DownloadState.Running -> {
                    val d = ui.download as DownloadState.Running
                    if (d.total > 0) {
                        LinearProgressIndicator(
                            progress = { (d.done.toFloat() / d.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (d.total > 0) "${gb(d.done.toDouble())} из ${gb(d.total.toDouble())} ГБ" else "Ожидание сети…",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onCancel) { Text("Отмена") }
                    }
                }
                ui.download is DownloadState.Failed -> {
                    Text(
                        ui.download.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onDownload, Modifier.padding(top = 8.dp)) { Text("Повторить") }
                }
                else -> Button(onDownload) { Text("Скачать") }
            }
        }
    }
}
