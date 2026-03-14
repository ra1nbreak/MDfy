package com.mdfy.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdfy.app.BuildConfig
import com.mdfy.app.R
import kotlinx.coroutines.launch

/**
 * Экран настроек MDfy.
 *
 * Секции (сверху вниз):
 *   1. Внешний вид  — тема
 *   2. Язык         — RU / EN, диалог выбора
 *   3. Воспроизведение — качество, кроссфейд, нормализация, визуализатор
 *   4. Загрузка     — качество скачивания, Wi-Fi only
 *   5. О приложении — версия, GitHub, Поддержать разработку
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showLangDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            // ── 1. Внешний вид ─────────────────────────────────────────────
            SettingsSection(
                title = stringResource(R.string.settings_section_appearance),
                icon = Icons.Default.Palette
            ) {
                ThemeSelector(
                    current = state.theme,
                    onSelect = viewModel::setTheme
                )
            }

            // ── 2. Язык ────────────────────────────────────────────────────
            SettingsSection(
                title = stringResource(R.string.settings_section_language),
                icon = Icons.Default.Language
            ) {
                LanguageRow(
                    current = state.language,
                    onClick = { showLangDialog = true }
                )
            }

            // ── 3. Воспроизведение ─────────────────────────────────────────
            SettingsSection(
                title = stringResource(R.string.settings_section_playback),
                icon = Icons.Default.MusicNote
            ) {
                SegmentedRow(
                    label = stringResource(R.string.settings_audio_quality),
                    options = listOf(
                        AudioQualitySetting.LOW    to stringResource(R.string.settings_audio_quality_low),
                        AudioQualitySetting.MEDIUM to stringResource(R.string.settings_audio_quality_medium),
                        AudioQualitySetting.HIGH   to stringResource(R.string.settings_audio_quality_high)
                    ),
                    selected = state.audioQuality,
                    onSelected = viewModel::setAudioQuality
                )
                SectionDivider()
                SwitchRow(
                    label = stringResource(R.string.settings_crossfade),
                    checked = state.crossfadeEnabled,
                    onCheckedChange = viewModel::setCrossfadeEnabled
                )
                AnimatedVisibility(
                    visible = state.crossfadeEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit  = fadeOut() + shrinkVertically()
                ) {
                    SliderRow(
                        label = stringResource(R.string.settings_crossfade_seconds, state.crossfadeSeconds),
                        value = state.crossfadeSeconds.toFloat(),
                        range = 0f..10f,
                        steps = 9,
                        onValueChange = { viewModel.setCrossfadeSeconds(it.toInt()) }
                    )
                }
                SectionDivider()
                SwitchRow(
                    label = stringResource(R.string.settings_normalise_volume),
                    checked = state.normaliseVolume,
                    onCheckedChange = viewModel::setNormaliseVolume
                )
                SectionDivider()
                SegmentedRow(
                    label = stringResource(R.string.settings_visualizer_style),
                    options = listOf(
                        VisualizerStyleSetting.BAR        to stringResource(R.string.settings_visualizer_bar),
                        VisualizerStyleSetting.WAVE       to stringResource(R.string.settings_visualizer_wave),
                        VisualizerStyleSetting.MIRROR_BAR to stringResource(R.string.settings_visualizer_mirror)
                    ),
                    selected = state.visualizerStyle,
                    onSelected = viewModel::setVisualizerStyle
                )
            }

            // ── 4. Загрузка ────────────────────────────────────────────────
            SettingsSection(
                title = stringResource(R.string.settings_section_downloads),
                icon = Icons.Default.Wifi
            ) {
                SwitchRow(
                    label    = stringResource(R.string.settings_download_wifi_only),
                    subtitle = stringResource(R.string.settings_download_wifi_only_desc),
                    checked  = state.wifiOnlyDownload,
                    onCheckedChange = viewModel::setWifiOnlyDownload
                )
            }

            // ── 5. О приложении ────────────────────────────────────────────
            SettingsSection(
                title = stringResource(R.string.settings_section_about),
                icon = Icons.Default.GraphicEq
            ) {
                // Версия (не кликабельная)
                InfoRow(
                    label = stringResource(R.string.settings_version),
                    value = BuildConfig.VERSION_NAME
                )
                SectionDivider()

                // GitHub — открывает браузер
                val githubUrl = stringResource(R.string.url_github)
                LinkRow(
                    label     = stringResource(R.string.settings_github),
                    subtitle  = stringResource(R.string.settings_github_desc),
                    icon      = Icons.Default.Code,
                    iconTint  = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick   = { context.openUrl(githubUrl) }
                )
                SectionDivider()

                // Поддержать разработку
                // url_support в strings.xml — замените на свою ссылку когда определитесь
                val supportUrl = stringResource(R.string.url_support)
                LinkRow(
                    label          = stringResource(R.string.settings_support),
                    subtitle       = stringResource(R.string.settings_support_desc),
                    icon           = Icons.Default.Favorite,
                    iconTint       = Color(0xFFFF6584),
                    containerColor = Color(0xFFFF6584).copy(alpha = 0.07f),
                    onClick        = { context.openUrl(supportUrl) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── Диалог выбора языка ──────────────────────────────────────────────
    if (showLangDialog) {
        LanguageDialog(
            current = state.language,
            onSelect = { lang ->
                val noRestartNeeded = viewModel.setLanguage(lang)
                showLangDialog = false
                if (!noRestartNeeded) {
                    scope.launch {
                        snackbar.showSnackbar(
                            context.getString(R.string.settings_language_restart_hint)
                        )
                    }
                }
            },
            onDismiss = { showLangDialog = false }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Компоненты
// ══════════════════════════════════════════════════════════════════════════════

/** Секция с заголовком, иконкой и карточкой-контейнером */
@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

/** Тонкий разделитель внутри секции */
@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

/** Выбор темы тремя плитками */
@Composable
private fun ThemeSelector(current: AppTheme, onSelect: (AppTheme) -> Unit) {
    val options = listOf(
        AppTheme.SYSTEM to stringResource(R.string.settings_theme_system),
        AppTheme.LIGHT  to stringResource(R.string.settings_theme_light),
        AppTheme.DARK   to stringResource(R.string.settings_theme_dark)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (theme, label) ->
            val isSelected = theme == current
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.03f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "themeScale"
            )
            val bg by animateColorAsState(
                targetValue = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                label = "themeBg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .scale(scale)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(theme) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Строка текущего языка с бейджем — открывает диалог по клику */
@Composable
private fun LanguageRow(current: AppLanguage, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        // Бейдж с текущим языком: displayName намеренно не переводится,
        // чтобы пользователь узнавал свой язык в любом интерфейсе
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = current.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

/** Диалог выбора из двух языков */
@Composable
private fun LanguageDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppLanguage.entries.forEach { lang ->
                    val isSelected = lang == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else Color.Transparent
                            )
                            .clickable { onSelect(lang) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // displayName не переводится — намеренно
                        Text(
                            text = lang.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** Сегментированный выбор (качество, стиль) */
@Composable
private fun <T> SegmentedRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (value, optLabel) ->
                val isSelected = value == selected
                val bg by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    label = "segBg"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .clickable { onSelected(value) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = optLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Switch-строка с опциональным subtitle */
@Composable
private fun SwitchRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Slider (кроссфейд и т.п.) */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Статичная строка «ключ — значение» (версия) */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Кликабельная строка-ссылка (GitHub, Поддержать разработку).
 * [containerColor] — лёгкая подложка всей строки для визуального акцента.
 */
@Composable
private fun LinkRow(
    label: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    containerColor: Color = Color.Transparent,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Иконка в круглом контейнере
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Extension ────────────────────────────────────────────────────────────────

/** Открывает URL в браузере. */
private fun android.content.Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
