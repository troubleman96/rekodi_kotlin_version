package com.camelcreatives.rekodi.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "rekodi_settings")

data class RekodiSettings(
    val videoResolution: String = "Auto",
    val videoFps: Int = 30,
    val videoBitrate: String = "Medium",
    val orientationLock: String = "Auto",
    val countdownEnabled: Boolean = true,
    val stopOnLockScreen: Boolean = false,
    val audioSource: String = "Mic+Internal",
    val sampleRate: Int = 44100,
    val audioChannels: String = "Stereo",
    val noiseSuppression: Boolean = false,
    val bubbleEnabled: Boolean = true,
    val bubbleOpacity: Float = 0.4f,
    val bubbleHideRecording: Boolean = false,
    val zoomEnabled: Boolean = false,
    val zoomStyle: String = "Ripple",
    val zoomColor: String = "#E8A33D",
    val zoomSensitivity: Int = 5,
    val autoDeleteDays: Int = 0,
    val storageMaxCapMb: Int = 0,
    val darkMode: String = "System",
    val dynamicColor: Boolean = true,
    val language: String = "en",
    val tapCountEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val VIDEO_RESOLUTION = stringPreferencesKey("video_resolution")
        val VIDEO_FPS = intPreferencesKey("video_fps")
        val VIDEO_BITRATE = stringPreferencesKey("video_bitrate")
        val ORIENTATION_LOCK = stringPreferencesKey("orientation_lock")
        val COUNTDOWN_ENABLED = booleanPreferencesKey("countdown_enabled")
        val STOP_ON_LOCK_SCREEN = booleanPreferencesKey("stop_on_lock_screen")
        val AUDIO_SOURCE = stringPreferencesKey("audio_source")
        val SAMPLE_RATE = intPreferencesKey("sample_rate")
        val AUDIO_CHANNELS = stringPreferencesKey("audio_channels")
        val NOISE_SUPPRESSION = booleanPreferencesKey("noise_suppression")
        val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        val BUBBLE_OPACITY = floatPreferencesKey("bubble_opacity")
        val BUBBLE_HIDE_RECORDING = booleanPreferencesKey("bubble_hide_recording")
        val ZOOM_ENABLED = booleanPreferencesKey("zoom_enabled")
        val ZOOM_STYLE = stringPreferencesKey("zoom_style")
        val ZOOM_COLOR = stringPreferencesKey("zoom_color")
        val ZOOM_SENSITIVITY = intPreferencesKey("zoom_sensitivity")
        val AUTO_DELETE_DAYS = intPreferencesKey("auto_delete_days")
        val STORAGE_MAX_CAP_MB = intPreferencesKey("storage_max_cap_mb")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LANGUAGE = stringPreferencesKey("language")
        val TAP_COUNT_ENABLED = booleanPreferencesKey("tap_count_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val settings: Flow<RekodiSettings> = context.dataStore.data.map { prefs ->
        RekodiSettings(
            videoResolution = prefs[VIDEO_RESOLUTION] ?: "Auto",
            videoFps = prefs[VIDEO_FPS] ?: 30,
            videoBitrate = prefs[VIDEO_BITRATE] ?: "Medium",
            orientationLock = prefs[ORIENTATION_LOCK] ?: "Auto",
            countdownEnabled = prefs[COUNTDOWN_ENABLED] ?: true,
            stopOnLockScreen = prefs[STOP_ON_LOCK_SCREEN] ?: false,
            audioSource = prefs[AUDIO_SOURCE] ?: "Mic+Internal",
            sampleRate = prefs[SAMPLE_RATE] ?: 44100,
            audioChannels = prefs[AUDIO_CHANNELS] ?: "Stereo",
            noiseSuppression = prefs[NOISE_SUPPRESSION] ?: false,
            bubbleEnabled = prefs[BUBBLE_ENABLED] ?: true,
            bubbleOpacity = prefs[BUBBLE_OPACITY] ?: 0.4f,
            bubbleHideRecording = prefs[BUBBLE_HIDE_RECORDING] ?: false,
            zoomEnabled = prefs[ZOOM_ENABLED] ?: false,
            zoomStyle = prefs[ZOOM_STYLE] ?: "Ripple",
            zoomColor = prefs[ZOOM_COLOR] ?: "#E8A33D",
            zoomSensitivity = prefs[ZOOM_SENSITIVITY] ?: 5,
            autoDeleteDays = prefs[AUTO_DELETE_DAYS] ?: 0,
            storageMaxCapMb = prefs[STORAGE_MAX_CAP_MB] ?: 0,
            darkMode = prefs[DARK_MODE] ?: "System",
            dynamicColor = prefs[DYNAMIC_COLOR] ?: true,
            language = prefs[LANGUAGE] ?: "en",
            tapCountEnabled = prefs[TAP_COUNT_ENABLED] ?: true,
            onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: false
        )
    }

    suspend fun updateVideoResolution(value: String) {
        context.dataStore.edit { it[VIDEO_RESOLUTION] = value }
    }
    suspend fun updateVideoFps(value: Int) {
        context.dataStore.edit { it[VIDEO_FPS] = value }
    }
    suspend fun updateVideoBitrate(value: String) {
        context.dataStore.edit { it[VIDEO_BITRATE] = value }
    }
    suspend fun updateOrientationLock(value: String) {
        context.dataStore.edit { it[ORIENTATION_LOCK] = value }
    }
    suspend fun updateCountdownEnabled(value: Boolean) {
        context.dataStore.edit { it[COUNTDOWN_ENABLED] = value }
    }
    suspend fun updateStopOnLockScreen(value: Boolean) {
        context.dataStore.edit { it[STOP_ON_LOCK_SCREEN] = value }
    }
    suspend fun updateAudioSource(value: String) {
        context.dataStore.edit { it[AUDIO_SOURCE] = value }
    }
    suspend fun updateSampleRate(value: Int) {
        context.dataStore.edit { it[SAMPLE_RATE] = value }
    }
    suspend fun updateAudioChannels(value: String) {
        context.dataStore.edit { it[AUDIO_CHANNELS] = value }
    }
    suspend fun updateNoiseSuppression(value: Boolean) {
        context.dataStore.edit { it[NOISE_SUPPRESSION] = value }
    }
    suspend fun updateBubbleEnabled(value: Boolean) {
        context.dataStore.edit { it[BUBBLE_ENABLED] = value }
    }
    suspend fun updateBubbleOpacity(value: Float) {
        context.dataStore.edit { it[BUBBLE_OPACITY] = value }
    }
    suspend fun updateBubbleHideRecording(value: Boolean) {
        context.dataStore.edit { it[BUBBLE_HIDE_RECORDING] = value }
    }
    suspend fun updateZoomEnabled(value: Boolean) {
        context.dataStore.edit { it[ZOOM_ENABLED] = value }
    }
    suspend fun updateZoomStyle(value: String) {
        context.dataStore.edit { it[ZOOM_STYLE] = value }
    }
    suspend fun updateZoomColor(value: String) {
        context.dataStore.edit { it[ZOOM_COLOR] = value }
    }
    suspend fun updateZoomSensitivity(value: Int) {
        context.dataStore.edit { it[ZOOM_SENSITIVITY] = value }
    }
    suspend fun updateAutoDeleteDays(value: Int) {
        context.dataStore.edit { it[AUTO_DELETE_DAYS] = value }
    }
    suspend fun updateStorageMaxCapMb(value: Int) {
        context.dataStore.edit { it[STORAGE_MAX_CAP_MB] = value }
    }
    suspend fun updateDarkMode(value: String) {
        context.dataStore.edit { it[DARK_MODE] = value }
    }
    suspend fun updateDynamicColor(value: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLOR] = value }
    }
    suspend fun updateLanguage(value: String) {
        context.dataStore.edit { it[LANGUAGE] = value }
    }
    suspend fun updateTapCountEnabled(value: Boolean) {
        context.dataStore.edit { it[TAP_COUNT_ENABLED] = value }
    }
    suspend fun updateOnboardingCompleted(value: Boolean) {
        context.dataStore.edit { it[ONBOARDING_COMPLETED] = value }
    }
}
