package com.riglineaimstriker.junior.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
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
import com.riglineaimstriker.junior.ui.home.HomeScreen
import com.riglineaimstriker.junior.ui.prepare.PrepareScreen
import com.riglineaimstriker.junior.ui.setup.SetupScreen
import com.riglineaimstriker.junior.ui.settings.SettingsScreen
import com.riglineaimstriker.junior.ui.skills.SkillsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NavViewModel @Inject constructor(prefs: AppPrefs, repo: ModelRepository) : ViewModel() {
    val startRoute: String = run {
        val p = Profiles.byId(prefs.selected.value)
        val needPrepare = (p == null || !repo.isInstalled(p)) && repo.hasBundled(Profiles.Mini)
        if (needPrepare) "prepare" else "home"
    }
}

@Composable
fun AppNav(vm: NavViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val start = remember { vm.startRoute }
    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = { fadeIn(tween(350)) + slideInHorizontally(tween(380)) { it / 6 } },
        exitTransition = { fadeOut(tween(250)) + scaleOut(tween(320), targetScale = 0.95f) },
        popEnterTransition = { fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.95f) },
        popExitTransition = { fadeOut(tween(250)) + slideOutHorizontally(tween(320)) { it / 6 } },
    ) {
        composable(
            "prepare",
            exitTransition = { fadeOut(tween(500)) + scaleOut(tween(500), targetScale = 1.08f) },
        ) {
            PrepareScreen(
                onDone = { nav.navigate("home") { popUpTo("prepare") { inclusive = true } } },
                onChooseModel = {
                    nav.navigate("home") { popUpTo("prepare") { inclusive = true } }
                    nav.navigate("setup")
                },
            )
        }
        composable(
            "home",
            enterTransition = { fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.96f) },
        ) {
            HomeScreen(
                onNewChat = { nav.navigate("chat") },
                onContinue = { nav.navigate("chat") },
                onModels = { nav.navigate("setup") },
                onSkills = { nav.navigate("skills") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("skills") {
            SkillsScreen(onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable("chat") {
            ChatScreen(
                onBack = { nav.popBackStack() },
                onOpenModels = { nav.navigate("setup") },
            )
        }
        composable(
            "setup",
            enterTransition = { slideInVertically(tween(420)) { it } + fadeIn(tween(300)) },
            popExitTransition = { slideOutVertically(tween(380)) { it } + fadeOut(tween(260)) },
        ) {
            SetupScreen(onDone = {
                nav.navigate("chat") {
                    popUpTo("home")
                    launchSingleTop = true
                }
            })
        }
    }
}
