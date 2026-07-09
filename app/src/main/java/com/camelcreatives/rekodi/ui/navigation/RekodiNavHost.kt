package com.camelcreatives.rekodi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.camelcreatives.rekodi.library.ui.LibraryScreen
import com.camelcreatives.rekodi.settings.SettingsScreen
import com.camelcreatives.rekodi.editor.ui.AudioEditorScreen
import com.camelcreatives.rekodi.onboarding.OnboardingScreen
import com.camelcreatives.rekodi.recorder.model.Recording

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val AUDIO_EDITOR = "audio_editor/{recordingId}"
    const val SETTINGS = "settings"
    const val RECORDING_DETAIL = "recording_detail/{recordingId}"

    fun audioEditor(recordingId: Long) = "audio_editor/$recordingId"
    fun recordingDetail(recordingId: Long) = "recording_detail/$recordingId"
}

@Composable
fun RekodiNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = { navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } } }
            )
        }
        composable(Routes.HOME) {
            LibraryScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToEditor = { recordingId -> navController.navigate(Routes.audioEditor(recordingId)) },
                onNavigateToDetail = { recordingId -> navController.navigate(Routes.recordingDetail(recordingId)) }
            )
        }
        composable(Routes.AUDIO_EDITOR) {
            AudioEditorScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.RECORDING_DETAIL) {
            com.camelcreatives.rekodi.library.ui.RecordingDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEditor = { recordingId -> navController.navigate(Routes.audioEditor(recordingId)) }
            )
        }
    }
}
