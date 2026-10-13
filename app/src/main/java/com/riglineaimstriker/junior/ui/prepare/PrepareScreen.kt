package com.riglineaimstriker.junior.ui.prepare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riglineaimstriker.junior.R
import com.riglineaimstriker.junior.model.AppPrefs
import com.riglineaimstriker.junior.model.ModelRepository
import com.riglineaimstriker.junior.model.Profiles
import com.riglineaimstriker.junior.ui.components.Illustration
import com.riglineaimstriker.junior.ui.components.InlineNotice
import com.riglineaimstriker.junior.ui.components.Space
import com.riglineaimstriker.junior.ui.components.formatGb
import com.riglineaimstriker.junior.ui.components.humanError
import com.riglineaimstriker.junior.ui.components.rememberReducedMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

/** Гигабайты по-русски с неразрывным пробелом: «1,2 ГБ». */
private fun gb(bytes: Long) = formatGb(bytes.toDouble())

/**
 * Первый запуск: копирование встроенной модели. Прогресс — только реальные байты из PrepareState.Copying;
 * пока размер неизвестен, показываем неопределённый индикатор без процентов.
 */
@Composable
fun PrepareScreen(
    onDone: () -> Unit,
    onChooseModel: () -> Unit,
    vm: PrepareViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state is PrepareState.Done) onDone() }
    val reduced = rememberReducedMotion()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.xl, vertical = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        when (val st = state) {
            is PrepareState.Failed -> {
                Illustration(R.drawable.il_error)
                Text(
                    "Не получилось подготовить модель",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                InlineNotice(humanError(st.message, "Не удалось скопировать модель."), isError = true)
                Text(
                    "Попробуйте ещё раз. Если ошибка повторится, выберите другую модель или скачайте её на экране моделей.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = vm::start, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Повторить")
                }
                OutlinedButton(onClick = onChooseModel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Выбрать другую модель")
                }
            }
            else -> {
                val c = st as? PrepareState.Copying
                Illustration(R.drawable.il_model)
                Text(
                    "Готовим Junior к работе",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Модель уже есть внутри приложения — переносим её в рабочую папку. " +
                        "Интернет не нужен, это делается один раз.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (c != null && c.total > 0) {
                    LinearProgressIndicator(
                        progress = { (c.done.toFloat() / c.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = Space.s),
                    )
                    Text(
                        "Скопировано ${gb(c.done)} из ${gb(c.total)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (!reduced) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Space.s))
                    Text(
                        "Начинаем…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
