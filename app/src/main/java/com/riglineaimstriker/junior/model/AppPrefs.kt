package com.riglineaimstriker.junior.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPrefs @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("junior", Context.MODE_PRIVATE)
    private val _selected = MutableStateFlow(sp.getString(KEY_MODEL, null))
    val selected: StateFlow<String?> = _selected.asStateFlow()

    fun select(id: String) {
        sp.edit().putString(KEY_MODEL, id).apply()
        _selected.value = id
    }

    fun clear() {
        sp.edit().remove(KEY_MODEL).apply()
        _selected.value = null
    }

    private companion object { const val KEY_MODEL = "selected_model" }
}
