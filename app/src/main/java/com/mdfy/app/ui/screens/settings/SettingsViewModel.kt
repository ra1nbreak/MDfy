package com.mdfy.app.ui.screens.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel экрана настроек MDfy.
 *
 * Все настройки хранятся в DataStore Preferences.
 *
 * Смена языка (RU / EN):
 *   Android 13+ → [LocaleManager.setApplicationLocales]  — без перезапуска
 *   Android 8–12 → [AppCompatDelegate.setApplicationLocales] — нужен перезапуск
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        // Ключи DataStore
        val KEY_THEME              = stringPreferencesKey("theme")
        val KEY_LANGUAGE           = stringPreferencesKey("language")
        val KEY_AUDIO_QUALITY      = stringPreferencesKey("audio_quality")
        val KEY_DOWNLOAD_QUALITY   = stringPreferencesKey("download_quality")
        val KEY_CROSSFADE_ENABLED  = booleanPreferencesKey("crossfade_enabled")
        val KEY_CROSSFADE_SECONDS  = intPreferencesKey("crossfade_seconds")
        val KEY_NORMALISE_VOLUME   = booleanPreferencesKey("normalise_volume")
        val KEY_WIFI_ONLY_DOWNLOAD = booleanPreferencesKey("wifi_only_download")
        val KEY_VISUALIZER_STYLE   = stringPreferencesKey("visualizer_style")
    }

    // Единый StateFlow UI-состояния — Compose подписывается через collectAsStateWithLifecycle()
    val uiState: StateFlow<SettingsUiState> = dataStore.data
        .map { prefs ->
            SettingsUiState(
                theme             = AppTheme.fromKey(prefs[KEY_THEME]),
                language          = AppLanguage.fromKey(prefs[KEY_LANGUAGE]),
                audioQuality      = AudioQualitySetting.fromKey(prefs[KEY_AUDIO_QUALITY]),
                downloadQuality   = AudioQualitySetting.fromKey(prefs[KEY_DOWNLOAD_QUALITY]),
                crossfadeEnabled  = prefs[KEY_CROSSFADE_ENABLED] ?: false,
                crossfadeSeconds  = prefs[KEY_CROSSFADE_SECONDS] ?: 2,
                normaliseVolume   = prefs[KEY_NORMALISE_VOLUME] ?: false,
                wifiOnlyDownload  = prefs[KEY_WIFI_ONLY_DOWNLOAD] ?: true,
                visualizerStyle   = VisualizerStyleSetting.fromKey(prefs[KEY_VISUALIZER_STYLE])
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

    // ── Обработчики ──────────────────────────────────────────────────────────

    fun setTheme(theme: AppTheme) = save { it[KEY_THEME] = theme.key }

    /**
     * Меняет язык приложения и сохраняет выбор в DataStore.
     *
     * Возвращает [true] если перезапуск НЕ нужен (Android 13+),
     * [false] если UI должен показать подсказку о перезапуске.
     */
    fun setLanguage(language: AppLanguage): Boolean {
        viewModelScope.launch {
            dataStore.edit { it[KEY_LANGUAGE] = language.code }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ — нативная per-app локаль, Activity перестраивается сама
            context.getSystemService(LocaleManager::class.java)
                .applicationLocales = LocaleList.forLanguageTags(language.code)
            true
        } else {
            // Android 8–12 — AppCompat реализация
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(language.code)
            )
            false // Нужна подсказка о перезапуске
        }
    }

    fun setAudioQuality(q: AudioQualitySetting)      = save { it[KEY_AUDIO_QUALITY] = q.key }
    fun setDownloadQuality(q: AudioQualitySetting)   = save { it[KEY_DOWNLOAD_QUALITY] = q.key }
    fun setCrossfadeEnabled(v: Boolean)              = save { it[KEY_CROSSFADE_ENABLED] = v }
    fun setCrossfadeSeconds(v: Int)                  = save { it[KEY_CROSSFADE_SECONDS] = v.coerceIn(0, 10) }
    fun setNormaliseVolume(v: Boolean)               = save { it[KEY_NORMALISE_VOLUME] = v }
    fun setWifiOnlyDownload(v: Boolean)              = save { it[KEY_WIFI_ONLY_DOWNLOAD] = v }
    fun setVisualizerStyle(s: VisualizerStyleSetting) = save { it[KEY_VISUALIZER_STYLE] = s.key }

    private fun save(block: suspend (MutablePreferences) -> Unit) {
        viewModelScope.launch { dataStore.edit { block(it) } }
    }
}

// ── UI State ─────────────────────────────────────────────────────────────────

data class SettingsUiState(
    val theme             : AppTheme               = AppTheme.SYSTEM,
    val language          : AppLanguage            = AppLanguage.RUSSIAN,
    val audioQuality      : AudioQualitySetting    = AudioQualitySetting.HIGH,
    val downloadQuality   : AudioQualitySetting    = AudioQualitySetting.HIGH,
    val crossfadeEnabled  : Boolean                = false,
    val crossfadeSeconds  : Int                    = 2,
    val normaliseVolume   : Boolean                = false,
    val wifiOnlyDownload  : Boolean                = true,
    val visualizerStyle   : VisualizerStyleSetting = VisualizerStyleSetting.MIRROR_BAR
)

// ── Sealed/Enum модели ───────────────────────────────────────────────────────

enum class AppTheme(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");
    companion object { fun fromKey(k: String?) = entries.find { it.key == k } ?: SYSTEM }
}

/**
 * Только RU и EN.
 * [code] — BCP-47 тег, используется в LocaleManager / AppCompatDelegate.
 * [displayName] — показывается в UI без перевода (намеренно: пользователь
 * должен узнать свой язык даже если интерфейс на другом языке).
 */
enum class AppLanguage(val key: String, val code: String, val displayName: String) {
    RUSSIAN("ru", "ru", "Русский"),
    ENGLISH("en", "en", "English");
    companion object { fun fromKey(k: String?) = entries.find { it.key == k } ?: RUSSIAN }
}

enum class AudioQualitySetting(val key: String) {
    LOW("low"), MEDIUM("medium"), HIGH("high");
    companion object { fun fromKey(k: String?) = entries.find { it.key == k } ?: HIGH }
}

enum class VisualizerStyleSetting(val key: String) {
    BAR("bar"), WAVE("wave"), MIRROR_BAR("mirror_bar");
    companion object { fun fromKey(k: String?) = entries.find { it.key == k } ?: MIRROR_BAR }
}
