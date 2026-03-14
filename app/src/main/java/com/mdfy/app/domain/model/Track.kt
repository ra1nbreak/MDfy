package com.mdfy.app.domain.model

/**
 * Доменная модель трека MDfy.
 * Объединяет метаданные из Spotify и аудио-поток из YouTube.
 */
data class Track(
    val id: String,                    // Spotify Track ID
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,           // URL обложки (из Spotify)
    val spotifyId: String,
    val spotifyUri: String,            // spotify:track:xxx
    val popularity: Int,               // 0–100
    val previewUrl: String?,           // 30-сек превью от Spotify
    val audioStream: AudioStream?,     // Полный поток с YouTube
    val isDownloaded: Boolean = false,
    val localPath: String? = null,     // Путь к локальному файлу
    val releaseYear: Int? = null,
    val isFavorite: Boolean = false
)

data class AudioStream(
    val youtubeVideoId: String,
    val streamUrl: String,
    val quality: AudioQuality,
    val expiresAt: Long = System.currentTimeMillis() + 6 * 60 * 60 * 1000L
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
}

enum class AudioQuality { LOW, MEDIUM, HIGH }

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,          // 0.0–1.0
    val positionMs: Long = 0L,
    val isBuffering: Boolean = false,
    val isShuffleOn: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.NONE
)

enum class RepeatMode { NONE, ALL, ONE }
