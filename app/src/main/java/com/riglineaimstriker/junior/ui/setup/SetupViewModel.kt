package com.riglineaimstriker.junior.ui.setup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riglineaimstriker.junior.model.AppPrefs
import com.riglineaimstriker.junior.model.DeviceTuner
import com.riglineaimstriker.junior.model.DownloadState
import com.riglineaimstriker.junior.model.ModelProfile
import com.riglineaimstriker.junior.model.ModelRepository
import com.riglineaimstriker.junior.model.Profiles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ProfileUi(
    val profile: ModelProfile,
    val runnable: Boolean,
    val recommended: Boolean,
    val download: DownloadState,
)

sealed interface ImportState {
    data object Idle : ImportState
    data class Running(val done: Long, val total: Long) : ImportState
    data class Failed(val message: String) : ImportState
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val repo: ModelRepository,
    private val prefs: AppPrefs,
    private val tuner: DeviceTuner,
) : ViewModel() {

    val selectedId: StateFlow<String?> = prefs.selected

    val items: StateFlow<List<ProfileUi>> =
        combine(Profiles.all.map { repo.downloadState(it) }) { states ->
            val rec = tuner.recommended()
            Profiles.all.mapIndexed { i, p ->
                ProfileUi(p, tuner.canRun(p), p == rec, states[i])
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun download(p: ModelProfile) = repo.startDownload(p)
    fun cancel(p: ModelProfile) = repo.cancel(p)

    fun delete(p: ModelProfile) {
        if (prefs.selected.value == p.id) prefs.clear()
        repo.delete(p)
    }

    fun use(p: ModelProfile) = prefs.select(p.id)

    private val _import = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _import.asStateFlow()

    private val _customReady = MutableStateFlow(repo.isInstalled(Profiles.Custom))
    val customReady: StateFlow<Boolean> = _customReady.asStateFlow()

    private val _imported = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val imported: SharedFlow<Unit> = _imported

    fun importModel(uri: Uri) {
        if (_import.value is ImportState.Running) return
        viewModelScope.launch {
            _import.value = ImportState.Running(0, 0)
            try {
                repo.importFromUri(uri, Profiles.Custom) { d, t -> _import.value = ImportState.Running(d, t) }
                _customReady.value = true
                _import.value = ImportState.Idle
                prefs.select(Profiles.Custom.id)
                _imported.tryEmit(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _import.value = ImportState.Failed(e.message ?: "Не удалось загрузить модель из файла")
            }
        }
    }

    fun deleteCustom() {
        if (prefs.selected.value == Profiles.Custom.id) prefs.clear()
        repo.delete(Profiles.Custom)
        _customReady.value = false
        _import.value = ImportState.Idle
    }
}
