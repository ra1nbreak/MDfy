package com.mdfy.app.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.mdfy.app.domain.model.Track
import com.mdfy.app.ui.components.VisualizerStyle
import com.mdfy.app.ui.components.VisualizerView
import kotlinx.coroutines.launch

/**
 * Главный экран библиотеки.
 *
 * Структура:
 * ┌─────────────────────────────────────────┐
 * │ ModalNavigationDrawer                    │
 * │  ┌──────┬────────────────────────────┐  │
 * │  │Drawer│   Основная область         │  │
 * │  │      │  ┌──────────────────────┐  │  │
 * │  │ Nav  │  │  SearchBar           │  │  │
 * │  │      │  ├──────────────────────┤  │  │
 * │  │      │  │  LazyColumn (треки)  │  │  │
 * │  │      │  │                      │  │  │
 * │  │      │  ├──────────────────────┤  │  │
 * │  │      │  │  VisualizerView      │  │  │
 * │  │      │  ├──────────────────────┤  │  │
 * │  │      │  │  PlayerControls      │  │  │
 * │  └──────┴──┴──────────────────────┘  │  │
 * └─────────────────────────────────────────┘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToPlayer: (Track) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // ── Навигационный Drawer ─────────────────────────────────────────────────
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppNavigationDrawer(
                currentUser = uiState.currentUser,
                onNavigateToSettings = {
                    scope.launch { drawerState.close() }
                    onNavigateToSettings()
                },
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        // ── Основной контент ─────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Верхняя панель с кнопкой меню
            LibraryTopBar(
                onMenuClick = { scope.launch { drawerState.open() } },
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChanged,
                onSearch = viewModel::search
            )

            // Список треков занимает всё доступное пространство
            TrackList(
                modifier = Modifier.weight(1f),
                tracks = uiState.tracks,
                currentTrack = uiState.currentTrack,
                isLoading = uiState.isLoading,
                onTrackClick = { track ->
                    viewModel.playTrack(track)
                    onNavigateToPlayer(track)
                },
                onDownloadClick = viewModel::downloadTrack,
                onFavoriteClick = viewModel::toggleFavorite
            )

            // Визуализатор — виден только во время воспроизведения
            AnimatedVisibility(
                visible = uiState.isPlaying,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                VisualizerView(
                    frequencyData = viewModel.frequencyData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    primaryColor = MaterialTheme.colorScheme.primary,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    height = 80.dp,
                    style = VisualizerStyle.MIRROR_BAR
                )
            }

            // Мини-плеер внизу экрана
            AnimatedVisibility(visible = uiState.currentTrack != null) {
                MiniPlayer(
                    track = uiState.currentTrack ?: return@AnimatedVisibility,
                    isPlaying = uiState.isPlaying,
                    progress = uiState.playbackProgress,
                    onPlayPauseClick = viewModel::togglePlayPause,
                    onTrackClick = { uiState.currentTrack?.let(onNavigateToPlayer) }
                )
            }
        }
    }
}

// ── Компоненты экрана ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    onMenuClick: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit
) {
    var searchActive by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Кнопка открытия Drawer (аватар пользователя)
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Открыть меню",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        // SearchBar занимает оставшееся пространство
        SearchBar(
            inputField = {
                androidx.compose.material3.SearchBarDefaults.InputField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onSearch = {
                        onSearch(it)
                        searchActive = false
                    },
                    expanded = searchActive,
                    onExpandedChange = { searchActive = it },
                    placeholder = { Text("Поиск треков, артистов...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                )
            },
            expanded = searchActive,
            onExpandedChange = { searchActive = it },
            modifier = Modifier.weight(1f)
        ) {
            // Здесь можно отображать подсказки/историю поиска
        }
    }
}

@Composable
private fun TrackList(
    modifier: Modifier = Modifier,
    tracks: List<Track>,
    currentTrack: Track?,
    isLoading: Boolean,
    onTrackClick: (Track) -> Unit,
    onDownloadClick: (Track) -> Unit,
    onFavoriteClick: (Track) -> Unit
) {
    val listState = rememberLazyListState()

    // Прокручиваем к текущему треку при его изменении
    LaunchedEffect(currentTrack) {
        currentTrack?.let { current ->
            val index = tracks.indexOfFirst { it.id == current.id }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(
            items = tracks,
            key = { _, track -> track.id }  // Стабильные ключи для корректных анимаций
        ) { index, track ->
            TrackListItem(
                track = track,
                isCurrentTrack = track.id == currentTrack?.id,
                onClick = { onTrackClick(track) },
                onDownloadClick = { onDownloadClick(track) },
                onFavoriteClick = { onFavoriteClick(track) }
            )
        }
    }
}

@Composable
private fun TrackListItem(
    track: Track,
    isCurrentTrack: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    // Анимируем высоту строки при выборе — выбранный трек чуть больше
    val itemHeight by animateDpAsState(
        targetValue = if (isCurrentTrack) 72.dp else 64.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "trackItemHeight"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight)
            .clickable(onClick = onClick),
        color = if (isCurrentTrack)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Обложка альбома
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = "Обложка ${track.album}",
                    modifier = Modifier.fillMaxSize()
                )
                // Индикатор текущего трека — пульсирующая точка
                if (isCurrentTrack) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            // Метаданные трека
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrentTrack) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isCurrentTrack)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Длительность
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )

            // Кнопки действий
            IconButton(onClick = onFavoriteClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = "В избранное",
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onDownloadClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Скачать",
                    modifier = Modifier.size(20.dp),
                    tint = if (track.isDownloaded)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onPlayPauseClick: () -> Unit,
    onTrackClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTrackClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp
    ) {
        Column {
            // Прогресс-бар (тонкая линия сверху)
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onPlayPauseClick) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Воспроизвести"
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavigationDrawer(
    currentUser: UserInfo?,
    onNavigateToSettings: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    // Узкая боковая панель (ширина 280dp — стандарт Material3)
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 24.dp)
    ) {
        // Аккаунт пользователя
        currentUser?.let { user ->
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = "Аватар",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Навигационные пункты
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Библиотека") },
            selected = true,
            onClick = onCloseDrawer
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.FavoriteBorder, contentDescription = null) },
            label = { Text("Избранное") },
            selected = false,
            onClick = { /* навигация */ onCloseDrawer() }
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Download, contentDescription = null) },
            label = { Text("Загруженное") },
            selected = false,
            onClick = { onCloseDrawer() }
        )

        Spacer(modifier = Modifier.weight(1f))

        // Настройки внизу Drawer
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Настройки") },
            selected = false,
            onClick = {
                onNavigateToSettings()
                onCloseDrawer()
            }
        )
    }
}

// ── Утилиты ──────────────────────────────────────────────────────────────────

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

// Заглушки для типов, которые определяются в других файлах
data class UserInfo(val displayName: String, val email: String, val avatarUrl: String?)
