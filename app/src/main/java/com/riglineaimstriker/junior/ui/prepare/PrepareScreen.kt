package com.riglineaimstriker.junior.ui.prepare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riglineaimstriker.junior.model.AppPrefs
import com.riglineaimstriker.junior.model.ModelRepository
import com.riglineaimstriker.junior.model.Profiles
import com.riglineaimstriker.junior.ui.components.JuniorAvatar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

sealed interface PrepareState {
    data class Copying(val done: Long, val total: Long) : PrepareState
    data class Failed(val message: String) : PrepareState
    data object Done : PrepareState
}

@HiltViewModel
class PrepareViewModel @Inject constructor(
    private val repo: ModelRepository,
    private val prefs: AppPrefs,
) : ViewModel() {

    private val profile = Profiles.Mini
    private val _state = MutableStateFlow<PrepareState>(PrepareState.Copying(0, 0))
    val state: StateFlow<PrepareState> = _state.asStateFlow()
    private var job: Job? = null

    init { start() }

    fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = PrepareState.Copying(0, 0)
            try {
                if (!repo.isInstalled(profile)) {
                    repo.installBundled(profile) { d, t -> _state.value = PrepareState.Copying(d, t) }
                }
                prefs.select(profile.id)
                _state.value = PrepareState.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = PrepareState.Failed(e.message ?: "Не удалось подготовить модель")
            }
        }
    }
}

private fun gb(bytes: Long) = String.format(Locale.US, "%.2f", bytes / 1e9)

@Composable
fun PrepareScreen(
    onDone: () -> Unit,
    onChooseModel: () -> Unit,
    vm: PrepareViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state is PrepareState.Done) onDone() }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JuniorAvatar(size = 72.dp)
        Spacer(Modifier.height(24.dp))
        when (val st = state) {
            is PrepareState.Failed -> {
                Text("Не получилось", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(st.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(24.dp))
                Button(onClick = vm::start) { Text("Повторить") }
                TextButton(onClick = onChooseModel) { Text("Выбрать другую модель") }
            }
            else -> {
                val c = st as? PrepareState.Copying
                Text("Первый запуск", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Готовим Junior. Модель уже внутри приложения, интернет не нужен. Это делается один раз.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                if (c != null && c.total > 0) {
                    LinearProgressIndicator(
                        progress = { (c.done.toFloat() / c.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${gb(c.done)} / ${gb(c.total)} ГБ", style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
