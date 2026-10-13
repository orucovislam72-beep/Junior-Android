package com.riglineaimstriker.junior.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riglineaimstriker.junior.model.AppPrefs
import com.riglineaimstriker.junior.model.DeviceTuner
import com.riglineaimstriker.junior.model.DownloadState
import com.riglineaimstriker.junior.model.ModelProfile
import com.riglineaimstriker.junior.model.ModelRepository
import com.riglineaimstriker.junior.model.Profiles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
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
}
