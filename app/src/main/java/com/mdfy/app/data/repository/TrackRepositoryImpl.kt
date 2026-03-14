package com.mdfy.app.data.repository

import android.util.Log
import com.mdfy.app.data.remote.spotify.SpotifyApi
import com.mdfy.app.data.remote.youtube.YoutubeSearchApi
import com.mdfy.app.data.remote.spotify.dto.SpotifyTrackDto
import com.mdfy.app.domain.model.Track
import com.mdfy.app.domain.model.AudioStream
import com.mdfy.app.domain.repository.TrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация гибридного поиска треков.
 *
 * Логика работы:
 *   1. Запрашиваем метаданные (обложка, лирика, популярность) из Spotify API
 *   2. Для каждого найденного трека формируем поисковый запрос к YouTube
 *   3. Выбираем лучший YouTube-результат (эвристика по совпадению длительности)
 *   4. Возвращаем [Track] с метаданными Spotify + URL потока YouTube
 *
 * ВАЖНО: Это имитация логики. В реальном приложении нужно соблюдать
 * ToS обоих сервисов. YouTube не предоставляет прямые аудио-потоки
 * через официальный API — для этого используются сторонние библиотеки
 * (например, NewPipe Extractor).
 */
@Singleton
class TrackRepositoryImpl @Inject constructor(
    private val spotifyApi: SpotifyApi,
    private val youtubeApi: YoutubeSearchApi
) : TrackRepository {

    companion object {
        private const val TAG = "TrackRepository"

        // Допустимое расхождение длительности трека (в миллисекундах)
        // Если YouTube-результат отличается по длине > 5 сек — вероятно, это не тот трек
        private const val DURATION_TOLERANCE_MS = 5000L

        // Максимальное количество YouTube-результатов для анализа
        private const val MAX_YOUTUBE_RESULTS = 5
    }

    /**
     * Ищет треки по запросу.
     * Возвращает список треков с метаданными Spotify и ссылками на YouTube-потоки.
     *
     * @param query Поисковый запрос (название, артист, альбом)
     * @param limit Максимальное количество результатов (по умолчанию 20)
     */
    override suspend fun searchTracks(
        query: String,
        limit: Int
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "Поиск треков: query='$query', limit=$limit")

            // ── Шаг 1: Получаем метаданные из Spotify ────────────────────────
            val spotifyTracks = searchSpotify(query, limit)
            Log.d(TAG, "Spotify вернул ${spotifyTracks.size} треков")

            // ── Шаг 2: Параллельно ищем YouTube-потоки для каждого трека ─────
            // coroutineScope позволяет запустить всё параллельно,
            // но дождаться завершения всех задач перед возвратом
            coroutineScope {
                spotifyTracks.map { spotifyTrack ->
                    async {
                        // Для каждого трека ищем YouTube-аудио
                        val audioStream = findYoutubeStream(
                            artist = spotifyTrack.artists.firstOrNull()?.name ?: "",
                            title = spotifyTrack.name,
                            durationMs = spotifyTrack.durationMs
                        )

                        // Объединяем данные: метаданные Spotify + поток YouTube
                        mapToTrack(spotifyTrack, audioStream)
                    }
                }.map { it.await() }
            }
        }
    }

    /**
     * Получает рекомендованные треки на основе seed-треков.
     * Используется для функции "Далее" и "Похожие треки".
     *
     * @param seedTrackIds Список Spotify ID треков-источников (до 5)
     */
    override suspend fun getRecommendations(
        seedTrackIds: List<String>
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "Запрос рекомендаций для: ${seedTrackIds.joinToString()}")

            val spotifyTracks = spotifyApi.getRecommendations(
                seedTracks = seedTrackIds.take(5).joinToString(","),
                limit = 20
            ).tracks

            coroutineScope {
                spotifyTracks.map { track ->
                    async {
                        val audioStream = findYoutubeStream(
                            artist = track.artists.firstOrNull()?.name ?: "",
                            title = track.name,
                            durationMs = track.durationMs
                        )
                        mapToTrack(track, audioStream)
                    }
                }.map { it.await() }
            }
        }
    }

    // ── Приватные методы ─────────────────────────────────────────────────────

    /**
     * Выполняет поиск в Spotify API.
     */
    private suspend fun searchSpotify(query: String, limit: Int): List<SpotifyTrackDto> {
        return try {
            spotifyApi.searchTracks(
                query = query,
                type = "track",
                limit = limit,
                market = "RU"           // Рынок влияет на доступность треков
            ).tracks.items
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка Spotify API: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Ищет аудио-поток на YouTube по названию и артисту.
     *
     * Стратегия поиска (порядок приоритетов):
     *   1. "{artist} - {title} (Official Audio)" — официальное аудио
     *   2. "{artist} - {title} (Lyrics)"         — официальное лирик-видео
     *   3. "{artist} - {title}"                  — любой результат
     *
     * Выбор лучшего результата:
     *   - Приоритет официальным каналам (VEVO, Official)
     *   - Проверка длительности (отклонение < DURATION_TOLERANCE_MS)
     *
     * @param artist  Имя исполнителя
     * @param title   Название трека
     * @param durationMs Длительность трека из Spotify (для верификации)
     * @return [AudioStream] с URL потока или null если ничего не найдено
     */
    private suspend fun findYoutubeStream(
        artist: String,
        title: String,
        durationMs: Long
    ): AudioStream? {
        // Формируем несколько вариантов запроса — от специфичного к общему
        val searchQueries = buildSearchQueries(artist, title)

        for (query in searchQueries) {
            Log.d(TAG, "YouTube поиск: '$query'")

            try {
                val results = youtubeApi.search(
                    query = query,
                    maxResults = MAX_YOUTUBE_RESULTS,
                    type = "video",
                    videoCategoryId = "10"  // Категория "Музыка" в YouTube
                )

                // Ищем наилучшее совпадение среди результатов
                val bestMatch = results.items
                    .asSequence()
                    .mapNotNull { video ->
                        // Получаем детали видео (длительность, качество)
                        val details = youtubeApi.getVideoDetails(video.id.videoId)
                        details.items.firstOrNull()?.let { detail ->
                            VideoCandidate(
                                videoId = video.id.videoId,
                                title = video.snippet.title,
                                channelTitle = video.snippet.channelTitle,
                                durationMs = parseIsoDuration(detail.contentDetails.duration),
                                isOfficialChannel = isOfficialChannel(video.snippet.channelTitle)
                            )
                        }
                    }
                    .filter { candidate ->
                        // Фильтруем по длительности: отсекаем явно неподходящие
                        val delta = kotlin.math.abs(candidate.durationMs - durationMs)
                        delta < DURATION_TOLERANCE_MS
                    }
                    .sortedWith(
                        // Приоритет: сначала официальные каналы, потом минимальное расхождение длины
                        compareByDescending<VideoCandidate> { it.isOfficialChannel }
                            .thenBy { kotlin.math.abs(it.durationMs - durationMs) }
                    )
                    .firstOrNull()

                if (bestMatch != null) {
                    Log.d(TAG, "Найдено: '${bestMatch.title}' (${bestMatch.channelTitle})")

                    // Получаем прямую ссылку на аудио-поток
                    // В реальном приложении здесь используется NewPipe Extractor или аналог
                    val streamUrl = extractAudioStreamUrl(bestMatch.videoId)

                    if (streamUrl != null) {
                        return AudioStream(
                            youtubeVideoId = bestMatch.videoId,
                            streamUrl = streamUrl,
                            quality = AudioQuality.HIGH
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ошибка поиска YouTube для '$query': ${e.message}")
                // Продолжаем со следующим вариантом запроса
            }
        }

        Log.w(TAG, "YouTube поток не найден для: $artist - $title")
        return null
    }

    /**
     * Формирует список поисковых запросов в порядке приоритета.
     */
    private fun buildSearchQueries(artist: String, title: String): List<String> {
        // Очищаем название от скобок с ремиксами/версиями для более точного поиска
        val cleanTitle = title
            .replace(Regex("\\(feat\\..*?\\)"), "")  // Убираем featured artists
            .replace(Regex("\\[.*?]"), "")             // Убираем [Remastered] и т.п.
            .trim()

        return listOf(
            "$artist - $cleanTitle Official Audio",   // Самый специфичный запрос
            "$artist - $cleanTitle Official Video",
            "$artist - $cleanTitle Lyrics",
            "$artist - $cleanTitle",                  // Запасной вариант
            "$cleanTitle $artist"                     // Инвертированный порядок
        )
    }

    /**
     * Определяет, является ли канал официальным (VEVO, Official и т.д.).
     * Официальные каналы приоритетнее — меньше шанс на ремиксы и кавера.
     */
    private fun isOfficialChannel(channelName: String): Boolean {
        val officialKeywords = listOf("vevo", "official", "records", "music", "entertainment")
        val lowerName = channelName.lowercase()
        return officialKeywords.any { lowerName.contains(it) }
    }

    /**
     * Извлекает прямой URL аудио-потока из YouTube видео.
     *
     * ВНИМАНИЕ: Это заглушка-имитация!
     * В production-приложении здесь должна быть реальная логика:
     *   - NewPipe Extractor (open-source библиотека)
     *   - yt-dlp через процесс или wrapper
     *   - Собственный backend с кешированием ссылок
     *
     * YouTube-ссылки на потоки живут ~6 часов, их нужно кешировать.
     */
    private suspend fun extractAudioStreamUrl(videoId: String): String? {
        // TODO: Интегрировать NewPipe Extractor или backend-сервис
        // Пример URL структуры YouTube audio stream:
        // https://rr{N}---sn-{hash}.googlevideo.com/videoplayback?...

        return "https://your-audio-proxy-service/stream/$videoId"
    }

    /**
     * Преобразует ISO 8601 длительность (PT3M45S) в миллисекунды.
     * YouTube API возвращает длительность в этом формате.
     */
    private fun parseIsoDuration(isoDuration: String): Long {
        // Регулярное выражение для PT{H}H{M}M{S}S
        val regex = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
        val match = regex.matchEntire(isoDuration) ?: return 0L

        val hours = match.groupValues[1].toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].toLongOrNull() ?: 0L
        val seconds = match.groupValues[3].toLongOrNull() ?: 0L

        return (hours * 3600 + minutes * 60 + seconds) * 1000L
    }

    /**
     * Маппинг DTO Spotify + AudioStream в доменную модель Track.
     */
    private fun mapToTrack(dto: SpotifyTrackDto, audioStream: AudioStream?): Track {
        return Track(
            id = dto.id,
            title = dto.name,
            artist = dto.artists.joinToString(", ") { it.name },
            album = dto.album.name,
            durationMs = dto.durationMs,
            artworkUrl = dto.album.images.firstOrNull()?.url,
            spotifyId = dto.id,
            spotifyUri = dto.uri,
            popularity = dto.popularity,
            previewUrl = dto.previewUrl,           // 30-секундное превью от Spotify
            audioStream = audioStream,              // Полный поток с YouTube
            isDownloaded = false,
            localPath = null
        )
    }

    // ── Вспомогательные data-классы ──────────────────────────────────────────

    /**
     * Кандидат YouTube-видео для выбора лучшего совпадения.
     */
    private data class VideoCandidate(
        val videoId: String,
        val title: String,
        val channelTitle: String,
        val durationMs: Long,
        val isOfficialChannel: Boolean
    )
}

// ── Доменные модели (domain/model/) ─────────────────────────────────────────

data class AudioStream(
    val youtubeVideoId: String,
    val streamUrl: String,        // Прямой URL аудио-потока
    val quality: AudioQuality,
    val expiresAt: Long = System.currentTimeMillis() + 6 * 60 * 60 * 1000 // +6 часов
) {
    /** Проверяет, не истёк ли срок действия ссылки */
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
}

enum class AudioQuality {
    LOW,    // ~48kbps
    MEDIUM, // ~128kbps
    HIGH    // ~256kbps
}
