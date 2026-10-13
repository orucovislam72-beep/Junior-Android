package com.riglineaimstriker.junior.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.riglineaimstriker.junior.agent.AgentScreen
import com.riglineaimstriker.junior.model.AppPrefs
import com.riglineaimstriker.junior.model.ChatStore
import com.riglineaimstriker.junior.model.ModelRepository
import com.riglineaimstriker.junior.model.Profiles
import com.riglineaimstriker.junior.ui.chat.ChatScreen
import com.riglineaimstriker.junior.ui.chat.ChatViewModel
import com.riglineaimstriker.junior.ui.chats.ChatListScreen
import com.riglineaimstriker.junior.ui.components.MOTION_MS
import com.riglineaimstriker.junior.ui.home.HomeScreen
import com.riglineaimstriker.junior.ui.prepare.PrepareScreen
import com.riglineaimstriker.junior.ui.setup.SetupScreen
import com.riglineaimstriker.junior.ui.settings.SettingsScreen
import com.riglineaimstriker.junior.ui.skills.SkillsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NavViewModel @Inject constructor(
    prefs: AppPrefs,
    repo: ModelRepository,
    private val store: ChatStore,
) : ViewModel() {
    /** Маршрут последнего чата (или нового, если истории нет). */
    fun lastChatRoute(): String = chatRoute(store.latestId())

    val startRoute: String = run {
        val p = Profiles.byId(prefs.selected.value)
        val needPrepare = (p == null || !repo.isInstalled(p)) && repo.hasBundled(Profiles.Mini)
        if (needPrepare) "prepare" else "home"
    }
}

fun chatRoute(id: String?): String = "chat/" + (id ?: ChatViewModel.NEW_CHAT)

@Composable
fun AppNav(vm: NavViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val start = remember { vm.startRoute }
    NavHost(
        navController = nav,
        startDestination = start,
        // Функциональные переходы 150–250 мс: короткий сдвиг и прозрачность, без масштабирования.
        enterTransition = { fadeIn(tween(MOTION_MS)) + slideInHorizontally(tween(MOTION_MS)) { it / 12 } },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = { fadeIn(tween(MOTION_MS)) },
        popExitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(MOTION_MS)) { it / 12 } },
    ) {
        composable(
            "prepare",
            exitTransition = { fadeOut(tween(MOTION_MS)) },
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
            enterTransition = { fadeIn(tween(MOTION_MS)) },
        ) {
            HomeScreen(
                onNewChat = { nav.navigate(chatRoute(null)) },
                onOpenChat = { id -> nav.navigate(chatRoute(id)) },
                onChats = { nav.navigate("chats") },
                onAgent = { nav.navigate("agent") },
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
        composable("chats") {
            ChatListScreen(
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate(chatRoute(id)) { popUpTo("home") } },
                onNew = { nav.navigate(chatRoute(null)) { popUpTo("home") } },
            )
        }
        composable(
            "chat/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) {
            ChatScreen(
                onBack = { nav.popBackStack() },
                onOpenModels = { nav.navigate("setup") },
                onOpenChats = { nav.navigate("chats") },
            )
        }
        composable("agent") {
            AgentScreen(
                onBack = { nav.popBackStack() },
                onOpenModels = { nav.navigate("setup") },
            )
        }
        composable(
            "setup",
            enterTransition = { slideInVertically(tween(MOTION_MS)) { it / 8 } + fadeIn(tween(MOTION_MS)) },
            popExitTransition = { slideOutVertically(tween(MOTION_MS)) { it / 8 } + fadeOut(tween(150)) },
        ) {
            SetupScreen(
                onDone = {
                    nav.navigate(vm.lastChatRoute()) {
                        popUpTo("home")
                        launchSingleTop = true
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
