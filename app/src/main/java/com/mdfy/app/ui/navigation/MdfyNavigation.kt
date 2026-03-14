package com.mdfy.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mdfy.app.ui.screens.library.LibraryScreen
import com.mdfy.app.ui.screens.settings.SettingsScreen

/**
 * Маршруты навигации MDfy.
 * sealed class гарантирует исчерпывающий when() при добавлении новых экранов.
 */
sealed class MdfyRoute(val route: String) {
    data object Library  : MdfyRoute("library")
    data object Settings : MdfyRoute("settings")
    data object Player   : MdfyRoute("player/{trackId}") {
        fun withTrack(trackId: String) = "player/$trackId"
    }
}

/**
 * Корневой NavHost приложения MDfy.
 * Стартовый экран — библиотека треков.
 */
@Composable
fun MdfyNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = MdfyRoute.Library.route
    ) {
        composable(MdfyRoute.Library.route) {
            LibraryScreen(
                onNavigateToSettings = {
                    navController.navigate(MdfyRoute.Settings.route)
                },
                onNavigateToPlayer = { track ->
                    navController.navigate(MdfyRoute.Player.withTrack(track.id))
                }
            )
        }

        composable(MdfyRoute.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Player экран — TODO: реализовать PlayerScreen
        composable(MdfyRoute.Player.route) {
            // PlayerScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
