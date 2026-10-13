package com.riglineaimstriker.junior.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.riglineaimstriker.junior.model.AppPrefs
import com.riglineaimstriker.junior.model.ModelRepository
import com.riglineaimstriker.junior.model.Profiles
import com.riglineaimstriker.junior.ui.chat.ChatScreen
import com.riglineaimstriker.junior.ui.setup.SetupScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NavViewModel @Inject constructor(prefs: AppPrefs, repo: ModelRepository) : ViewModel() {
    val startRoute: String = run {
        val p = Profiles.byId(prefs.selected.value)
        if (p != null && repo.isInstalled(p)) "chat" else "setup"
    }
}

@Composable
fun AppNav(vm: NavViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val start = remember { vm.startRoute }
    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = { fadeIn() + slideInHorizontally { it / 8 } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() + slideOutHorizontally { it / 8 } },
    ) {
        composable("setup") {
            SetupScreen(onDone = {
                if (!nav.popBackStack("chat", inclusive = false)) {
                    nav.navigate("chat") { popUpTo("setup") { inclusive = true } }
                }
            })
        }
        composable("chat") { ChatScreen(onOpenModels = { nav.navigate("setup") }) }
    }
}
