package com.mdfy.app.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mdfy.app.audio.VisualizerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground Service для воспроизведения музыки.
 *
 * Почему MediaSessionService, а не простой Service?
 * - Автоматически управляет MediaNotification (уведомление с контролами)
 * - Интегрируется с Android MediaSession API (Bluetooth, Android Auto, WearOS)
 * - Поддерживает MediaController из любого компонента приложения
 * - Корректно обрабатывает focus-события (звонок, другое приложение)
 *
 * Манифест: android:foregroundServiceType="mediaPlayback"
 */
@UnstableApi
@AndroidEntryPoint
class MusicPlayerService : MediaSessionService() {

    companion object {
        private const val TAG = "MusicPlayerService"

        // Кастомные команды для управления визуализатором
        const val COMMAND_VISUALIZER_START = "com.musicplayer.VISUALIZER_START"
        const val COMMAND_VISUALIZER_STOP = "com.musicplayer.VISUALIZER_STOP"
    }

    // Hilt инжектирует менеджер визуализатора
    @Inject
    lateinit var visualizerManager: VisualizerManager

    // ExoPlayer и MediaSession создаются вручную (не через Hilt — они привязаны к Service lifecycle)
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    // ── Жизненный цикл Service ───────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MusicPlayerService: onCreate")

        initPlayer()
        initMediaSession()
    }

    override fun onDestroy() {
        Log.d(TAG, "MusicPlayerService: onDestroy")

        // КРИТИЧЕСКИ ВАЖНО: освобождать ресурсы в правильном порядке
        visualizerManager.release()     // 1. Останавливаем визуализатор
        mediaSession.release()          // 2. Освобождаем сессию
        player.release()               // 3. Освобождаем плеер

        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    // ── Инициализация ────────────────────────────────────────────────────────

    /**
     * Создаёт и настраивает ExoPlayer.
     */
    private fun initPlayer() {
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                // Говорим системе, что это музыкальный плеер
                // USAGE_MEDIA + CONTENT_TYPE_MUSIC = правильный routing и focus
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true  // ExoPlayer сам управляет audio focus
            )
            .setHandleAudioBecomingNoisy(true)  // Пауза при отключении наушников
            .build()

        // Слушаем события плеера для синхронизации с визуализатором
        player.addListener(createPlayerListener())
    }

    /**
     * Создаёт MediaSession с кастомными командами.
     */
    private fun initMediaSession() {
        // PendingIntent для открытия MainActivity при нажатии на уведомление
        val sessionActivityPendingIntent = packageManager
            ?.getLaunchIntentForPackage(packageName)
            ?.let { intent ->
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent!!)
            .setCallback(createSessionCallback())
            .build()
    }

    /**
     * Слушатель событий ExoPlayer.
     * Синхронизирует состояние визуализатора с состоянием воспроизведения.
     */
    private fun createPlayerListener() = object : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    Log.d(TAG, "Player: STATE_READY")
                    // Привязываем визуализатор к audio session ТОЛЬКО когда плеер готов
                    // Иначе audioSessionId может быть некорректным
                    visualizerManager.attach(player)
                    if (player.isPlaying) visualizerManager.start()
                }
                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "Player: STATE_BUFFERING")
                    // При буферизации гасим визуализатор, но не освобождаем
                    visualizerManager.stop()
                }
                Player.STATE_ENDED, Player.STATE_IDLE -> {
                    Log.d(TAG, "Player: STATE_ENDED / IDLE")
                    visualizerManager.stop()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "Player: isPlaying=$isPlaying")
            if (isPlaying) {
                visualizerManager.start()
            } else {
                visualizerManager.stop()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            Log.d(TAG, "Player: переключение трека -> ${mediaItem?.mediaMetadata?.title}")
            // При смене трека переподключаем визуализатор (audio session может измениться)
            visualizerManager.stop()
            if (player.isPlaying) {
                visualizerManager.attach(player)
                visualizerManager.start()
            }
        }
    }

    /**
     * Callback для обработки кастомных команд от MediaController.
     */
    private fun createSessionCallback() = object : MediaSession.Callback {

        /**
         * Регистрируем кастомные команды при подключении контроллера.
         */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val availableCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(SessionCommand(COMMAND_VISUALIZER_START, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_VISUALIZER_STOP, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableCommands)
                .build()
        }

        /**
         * Обработка кастомных команд от UI.
         */
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                COMMAND_VISUALIZER_START -> {
                    visualizerManager.start()
                    Log.d(TAG, "Визуализатор запущен по команде контроллера")
                }
                COMMAND_VISUALIZER_STOP -> {
                    visualizerManager.stop()
                    Log.d(TAG, "Визуализатор остановлен по команде контроллера")
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}
