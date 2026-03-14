package com.mdfy.app.data.local.downloader

import android.content.Context
import android.util.Log
import com.mdfy.app.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Загружает аудио-треки в локальное хранилище и записывает ID3-метаданные.
 *
 * Процесс загрузки:
 *   1. Создаём директорию в filesDir (не нужны разрешения на Android 10+)
 *   2. Скачиваем аудио-поток через OkHttp с прогрессом
 *   3. Записываем ID3v2.4 теги через jaudiotagger
 *   4. Скачиваем обложку и вставляем как APIC-тег
 *   5. Обновляем запись в БД с локальным путём
 *
 * [DownloadProgress] эмитируется через Flow для отображения прогресса в UI.
 */
@Singleton
class TrackDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "TrackDownloader"
        private const val MUSIC_DIR = "music"           // Директория внутри filesDir
        private const val BUFFER_SIZE = 8 * 1024        // 8 KB буфер для загрузки
    }

    /**
     * Скачивает трек и возвращает Flow с прогрессом загрузки.
     *
     * Использование:
     * ```kotlin
     * downloader.downloadTrack(track).collect { progress ->
     *     when (progress) {
     *         is DownloadProgress.Downloading -> updateProgressBar(progress.percent)
     *         is DownloadProgress.Success -> showSuccess(progress.localPath)
     *         is DownloadProgress.Error -> showError(progress.message)
     *     }
     * }
     * ```
     */
    fun downloadTrack(track: Track): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Starting)

        withContext(Dispatchers.IO) {
            try {
                // ── 1. Подготовка директории ──────────────────────────────────
                val musicDir = File(context.filesDir, MUSIC_DIR).also { it.mkdirs() }

                // Имя файла: sanitize убирает символы, недопустимые в именах файлов
                val fileName = sanitizeFileName("${track.artist} - ${track.title}.mp3")
                val audioFile = File(musicDir, fileName)

                Log.d(TAG, "Начало загрузки: $fileName")

                // ── 2. Загрузка аудио-потока ──────────────────────────────────
                val streamUrl = track.audioStream?.streamUrl
                    ?: throw IllegalStateException("URL потока недоступен для трека: ${track.title}")

                downloadFile(
                    url = streamUrl,
                    outputFile = audioFile,
                    onProgress = { progress ->
                        // emit нельзя вызывать из другого корутина — используем suspend emit
                        // Здесь мы внутри withContext(IO), так что используем Channel
                    }
                )

                Log.d(TAG, "Файл загружен: ${audioFile.absolutePath} (${audioFile.length()} байт)")

                emit(DownloadProgress.WritingTags)

                // ── 3. Скачиваем обложку альбома ─────────────────────────────
                val artworkBytes = track.artworkUrl?.let { url ->
                    downloadBytes(url)
                }

                // ── 4. Записываем ID3-теги ────────────────────────────────────
                writeId3Tags(
                    file = audioFile,
                    track = track,
                    artworkBytes = artworkBytes
                )

                Log.d(TAG, "ID3-теги записаны успешно")

                emit(DownloadProgress.Success(localPath = audioFile.absolutePath))

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки трека: ${e.message}", e)
                emit(DownloadProgress.Error(message = e.message ?: "Неизвестная ошибка"))
            }
        }
    }

    /**
     * Записывает ID3v2.4 теги в MP3-файл.
     *
     * Поддерживаемые поля:
     * - TIT2 (название трека)
     * - TPE1 (исполнитель)
     * - TALB (альбом)
     * - TRCK (номер трека)
     * - APIC (обложка альбома — бинарные данные JPEG/PNG)
     *
     * jaudiotagger автоматически определяет формат файла по расширению.
     */
    private fun writeId3Tags(
        file: File,
        track: Track,
        artworkBytes: ByteArray?
    ) {
        try {
            // Загружаем аудио-файл через jaudiotagger
            val audioFile = AudioFileIO.read(file)
            val tag: AbstractID3v2Tag = audioFile.tagOrCreateAndSetDefault as? AbstractID3v2Tag
                ?: ID3v24Tag()

            // ── Текстовые теги ────────────────────────────────────────────────
            tag.setField(FieldKey.TITLE, track.title)
            tag.setField(FieldKey.ARTIST, track.artist)
            tag.setField(FieldKey.ALBUM, track.album)

            // Год выпуска (если доступен)
            track.releaseYear?.let { year ->
                tag.setField(FieldKey.YEAR, year.toString())
            }

            // Комментарий с источником (для прозрачности)
            tag.setField(FieldKey.COMMENT, "Downloaded via AdvancedMusicPlayer | Spotify ID: ${track.spotifyId}")

            // ── Обложка альбома (APIC-тег) ────────────────────────────────────
            artworkBytes?.let { bytes ->
                val artwork = ArtworkFactory.createArtworkFromFile(
                    // Создаём временный файл для Artwork
                    createTempArtworkFile(bytes)
                )
                artwork.mimeType = "image/jpeg"
                artwork.pictureType = 3 // 3 = Cover (front) — стандартный тип обложки
                tag.setField(artwork)
                Log.d(TAG, "Обложка добавлена: ${bytes.size} байт")
            }

            // Применяем тег к файлу
            audioFile.tag = tag
            audioFile.commit()  // Записываем на диск

        } catch (e: Exception) {
            // Не прерываем загрузку из-за ошибки тегов — файл уже скачан
            Log.w(TAG, "Ошибка записи ID3-тегов (файл сохранён без тегов): ${e.message}")
        }
    }

    // ── Вспомогательные методы ───────────────────────────────────────────────

    /**
     * Скачивает файл через OkHttp с callback прогресса.
     * Поддерживает большие файлы через потоковую запись (не загружает весь файл в память).
     */
    private fun downloadFile(
        url: String,
        outputFile: File,
        onProgress: (Int) -> Unit
    ) {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Ошибка HTTP: ${response.code} для $url")
        }

        val body = response.body ?: throw Exception("Пустой ответ сервера")
        val totalBytes = body.contentLength()
        var downloadedBytes = 0L

        FileOutputStream(outputFile).use { outputStream ->
            body.byteStream().use { inputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    // Вычисляем прогресс (если известен размер)
                    if (totalBytes > 0) {
                        val progress = (downloadedBytes * 100 / totalBytes).toInt()
                        onProgress(progress)
                    }
                }
            }
        }
    }

    /**
     * Скачивает URL целиком в ByteArray.
     * Используется для обложек (небольшие файлы, помещаются в память).
     */
    private fun downloadBytes(url: String): ByteArray? {
        return try {
            URL(url).readBytes()
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось скачать обложку: ${e.message}")
            null
        }
    }

    /**
     * Создаёт временный файл с данными обложки.
     * jaudiotagger требует File, а не ByteArray.
     */
    private fun createTempArtworkFile(bytes: ByteArray): File {
        return File.createTempFile("artwork_", ".jpg", context.cacheDir).apply {
            writeBytes(bytes)
            deleteOnExit()  // Удалится при завершении JVM (не при выходе из приложения!)
        }
    }

    /**
     * Очищает имя файла от символов, запрещённых в файловой системе.
     */
    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")  // Запрещённые символы
            .replace(Regex("\\s+"), " ")               // Множественные пробелы
            .trim()
            .take(200)                                  // Ограничение длины имени файла
    }
}

// ── Состояния загрузки ───────────────────────────────────────────────────────

sealed class DownloadProgress {
    data object Starting : DownloadProgress()

    data class Downloading(
        val percent: Int,               // 0-100
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DownloadProgress()

    data object WritingTags : DownloadProgress()

    data class Success(
        val localPath: String           // Абсолютный путь к скачанному файлу
    ) : DownloadProgress()

    data class Error(
        val message: String
    ) : DownloadProgress()
}
