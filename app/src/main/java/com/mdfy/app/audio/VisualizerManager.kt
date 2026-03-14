package com.mdfy.app.audio

import android.media.audiofx.Visualizer
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Управляет Android Visualizer API и предоставляет данные о частотах для UI.
 *
 * Жизненный цикл:
 *   1. attach(player) — вызывается при старте плеера
 *   2. start() / stop() — при старте/паузе воспроизведения
 *   3. release() — при уничтожении сервиса
 *
 * Данные публикуются через [frequencyData] — StateFlow<FloatArray>.
 * UI подписывается на этот поток и перерисовывает Canvas.
 */
@Singleton
class VisualizerManager @Inject constructor() {

    companion object {
        private const val TAG = "VisualizerManager"

        // Размер захвата данных — степень двойки (256, 512, 1024)
        // 512 — хороший баланс между точностью и производительностью
        private const val CAPTURE_SIZE = 512

        // Частота обновления: MAX_CAPTURE_RATE = максимально возможная скорость
        // Делим на 2 (~45-60 FPS) чтобы не перегружать UI-поток
        private val CAPTURE_RATE get() = Visualizer.getMaxCaptureRate() / 2
    }

    // ── Внутренний корутин-скоуп для фоновой обработки ─────────────────────
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── Визуализатор Android AudioFX ────────────────────────────────────────
    private var visualizer: Visualizer? = null

    // ── Поток данных частот для UI (нормализованные значения 0.0–1.0) ───────
    private val _frequencyData = MutableStateFlow(FloatArray(CAPTURE_SIZE / 2))
    val frequencyData: StateFlow<FloatArray> = _frequencyData.asStateFlow()

    // ── Поток данных формы волны для альтернативного отображения ────────────
    private val _waveformData = MutableStateFlow(FloatArray(CAPTURE_SIZE))
    val waveformData: StateFlow<FloatArray> = _waveformData.asStateFlow()

    // ── Флаг активности ─────────────────────────────────────────────────────
    private var isActive = false

    /**
     * Привязывает визуализатор к сессии ExoPlayer.
     *
     * ВАЖНО: вызывать ПОСЛЕ того как ExoPlayer начал воспроизведение,
     * иначе audioSessionId будет равен AudioManager.AUDIO_SESSION_ID_GENERATE (0),
     * что означает «глобальный микс» — нежелательно.
     *
     * @param player Экземпляр ExoPlayer из MusicPlayerService
     */
    @UnstableApi
    fun attach(player: ExoPlayer) {
        val audioSessionId = player.audioSessionId

        if (audioSessionId == 0) {
            Log.w(TAG, "audioSessionId = 0, визуализатор будет слушать глобальный микс")
        }

        // Освобождаем старый визуализатор если был
        releaseVisualizer()

        try {
            visualizer = Visualizer(audioSessionId).apply {
                // Устанавливаем размер буфера захвата
                captureSize = CAPTURE_SIZE

                // Регистрируем слушатель данных
                // MEASUREMENT_MODE_NONE — отключаем встроенные измерения (используем свои)
                setDataCaptureListener(
                    createDataListener(),
                    CAPTURE_RATE,
                    waveform = true,   // Захватываем форму волны
                    fft = true         // Захватываем частотный спектр (FFT)
                )

                // Включаем масштабирование — данные будут в диапазоне 0-255
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED

                Log.d(TAG, "Визуализатор создан: sessionId=$audioSessionId, captureSize=$captureSize")
            }
        } catch (e: Exception) {
            // На некоторых устройствах Visualizer недоступен (права, ограничения OEM)
            Log.e(TAG, "Ошибка создания Visualizer: ${e.message}", e)
        }
    }

    /**
     * Запускает захват данных.
     * Вызывать при начале воспроизведения (Player.STATE_READY + isPlaying = true).
     */
    fun start() {
        if (isActive) return
        try {
            visualizer?.enabled = true
            isActive = true
            Log.d(TAG, "Визуализатор запущен")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска Visualizer: ${e.message}")
        }
    }

    /**
     * Останавливает захват данных.
     * Вызывать при паузе, чтобы не тратить ресурсы CPU.
     */
    fun stop() {
        if (!isActive) return
        try {
            visualizer?.enabled = false
            isActive = false
            // Плавно гасим столбики: заполняем нулями
            scope.launch {
                _frequencyData.emit(FloatArray(_frequencyData.value.size))
                _waveformData.emit(FloatArray(_waveformData.value.size))
            }
            Log.d(TAG, "Визуализатор остановлен")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка остановки Visualizer: ${e.message}")
        }
    }

    /**
     * Освобождает все ресурсы.
     * ОБЯЗАТЕЛЬНО вызывать в onDestroy() сервиса!
     */
    fun release() {
        stop()
        releaseVisualizer()
        scope.cancel()
        Log.d(TAG, "VisualizerManager освобождён")
    }

    // ── Приватные методы ─────────────────────────────────────────────────────

    /**
     * Создаёт слушатель данных FFT и Waveform.
     */
    private fun createDataListener() = object : Visualizer.OnDataCaptureListener {

        /**
         * Вызывается с данными формы волны (временная область).
         * bytes[] — PCM-амплитуды, значения 0-255 (центр = 128).
         */
        override fun onWaveFormDataCapture(
            visualizer: Visualizer,
            waveform: ByteArray,
            samplingRate: Int
        ) {
            scope.launch {
                // Нормализуем в диапазон -1.0 .. 1.0
                val normalized = FloatArray(waveform.size) { i ->
                    (waveform[i].toInt() and 0xFF - 128) / 128f
                }
                _waveformData.emit(normalized)
            }
        }

        /**
         * Вызывается с данными FFT (частотная область).
         * fft[] — байты в формате: [DC, real1, imag1, real2, imag2, ...]
         * Первый байт — DC-компонента (постоянная составляющая).
         */
        override fun onFftDataCapture(
            visualizer: Visualizer,
            fft: ByteArray,
            samplingRate: Int
        ) {
            scope.launch {
                val magnitudes = computeMagnitudes(fft)
                _frequencyData.emit(magnitudes)
            }
        }
    }

    /**
     * Вычисляет амплитуды из FFT-данных.
     * Преобразует комплексные числа (re, im) в модули и нормализует до 0.0–1.0.
     *
     * Алгоритм: |z| = sqrt(re² + im²) / 128
     */
    private fun computeMagnitudes(fft: ByteArray): FloatArray {
        // FFT возвращает N/2 + 1 комплексных чисел для N точек
        // Нам нужны только первые N/2 (полезный спектр без зеркального отражения)
        val halfSize = fft.size / 2
        val magnitudes = FloatArray(halfSize)

        // Первый байт — DC-компонента, обрабатываем отдельно
        magnitudes[0] = (fft[0].toInt() and 0xFF) / 128f

        // Обрабатываем пары (real, imaginary)
        for (i in 1 until halfSize) {
            val real = fft[2 * i].toFloat()
            val imaginary = fft[2 * i + 1].toFloat()
            // Вычисляем модуль комплексного числа, нормализуем в 0..1
            magnitudes[i] = kotlin.math.sqrt(real * real + imaginary * imaginary) / 128f
        }

        return magnitudes
    }

    private fun releaseVisualizer() {
        try {
            visualizer?.release()
            visualizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка освобождения Visualizer: ${e.message}")
        }
    }
}
