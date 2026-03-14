package com.mdfy.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Компонент визуализатора на базе Jetpack Compose Canvas.
 *
 * Особенности:
 * - Использует [Animatable] для каждого столбика — плавная интерполяция между кадрами
 * - Поддерживает два режима: BAR (столбики) и WAVE (волна)
 * - Градиентная заливка с настраиваемыми цветами
 * - Эффект "пик" — маленький маркер над столбиком, который медленно падает вниз
 *
 * @param frequencyData StateFlow с нормализованными данными FFT (0.0–1.0)
 * @param modifier Модификатор Compose
 * @param barCount Количество видимых столбиков (реальных FFT-бинов больше)
 * @param barColor Цвет столбиков
 * @param height Высота компонента
 * @param style Стиль отображения: BAR или WAVE
 */
@Composable
fun VisualizerView(
    frequencyData: StateFlow<FloatArray>,
    modifier: Modifier = Modifier,
    barCount: Int = 64,
    primaryColor: Color = Color(0xFF6C63FF),
    accentColor: Color = Color(0xFFFF6584),
    height: Dp = 120.dp,
    style: VisualizerStyle = VisualizerStyle.BAR
) {
    // Подписываемся на поток данных частот
    val rawData by frequencyData.collectAsState()

    // ── Создаём анимируемые значения для каждого столбика ────────────────────
    // remember гарантирует, что Animatable-объекты не пересоздаются при рекомпозиции
    val animatedBars = remember(barCount) {
        Array(barCount) { Animatable(0f) }
    }

    // ── Значения "пиков" — маркеры максимума, которые медленно опускаются ─────
    val peakValues = remember(barCount) {
        Array<Animatable<Float, AnimationVector1D>>(barCount) { Animatable(0f) }
    }

    // ── Анимируем столбики при каждом обновлении данных ──────────────────────
    LaunchedEffect(rawData) {
        if (rawData.isEmpty()) return@LaunchedEffect

        // Децимируем FFT-данные: берём каждый N-й бин для нужного количества столбиков
        val step = (rawData.size / barCount).coerceAtLeast(1)

        for (i in 0 until barCount) {
            val binIndex = (i * step).coerceAtMost(rawData.size - 1)
            // Логарифмическое масштабирование — человеческое ухо воспринимает громкость логарифмически
            val rawValue = rawData[binIndex]
            val targetValue = (rawValue * rawValue).coerceIn(0f, 1f) // Квадрат для усиления контраста

            // Запускаем анимацию для каждого столбика параллельно
            launch {
                animatedBars[i].animateTo(
                    targetValue = targetValue,
                    animationSpec = tween(
                        durationMillis = 80,         // Быстрый подъём
                        easing = FastOutSlowInEasing
                    )
                )
            }

            // Обновляем "пик" только если новое значение выше текущего
            launch {
                if (targetValue > peakValues[i].value) {
                    peakValues[i].snapTo(targetValue) // Мгновенно поднимаем пик
                } else {
                    // Медленно опускаем пик вниз (эффект "падения")
                    peakValues[i].animateTo(
                        targetValue = (peakValues[i].value - 0.02f).coerceAtLeast(0f),
                        animationSpec = tween(durationMillis = 500)
                    )
                }
            }
        }
    }

    // ── Canvas для отрисовки ──────────────────────────────────────────────────
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val barValues = animatedBars.map { it.value }
        val peaks = peakValues.map { it.value }

        when (style) {
            VisualizerStyle.BAR -> drawBars(
                bars = barValues,
                peaks = peaks,
                primaryColor = primaryColor,
                accentColor = accentColor
            )
            VisualizerStyle.WAVE -> drawWave(
                bars = barValues,
                primaryColor = primaryColor,
                accentColor = accentColor
            )
            VisualizerStyle.MIRROR_BAR -> drawMirrorBars(
                bars = barValues,
                primaryColor = primaryColor,
                accentColor = accentColor
            )
        }
    }
}

// ── Стили визуализатора ──────────────────────────────────────────────────────

enum class VisualizerStyle {
    BAR,        // Классические столбики снизу вверх
    WAVE,       // Плавная волна
    MIRROR_BAR  // Симметричные столбики (сверху и снизу)
}

// ── Функции отрисовки ────────────────────────────────────────────────────────

/**
 * Отрисовывает классические вертикальные столбики с градиентом и пиками.
 */
private fun DrawScope.drawBars(
    bars: List<Float>,
    peaks: List<Float>,
    primaryColor: Color,
    accentColor: Color
) {
    val barCount = bars.size
    if (barCount == 0) return

    // Рассчитываем размеры: оставляем зазор 2dp между столбиками
    val gapPx = 2.dp.toPx()
    val totalGap = gapPx * (barCount - 1)
    val barWidth = ((size.width - totalGap) / barCount).coerceAtLeast(1f)
    val canvasHeight = size.height

    bars.forEachIndexed { index, magnitude ->
        val left = index * (barWidth + gapPx)
        val barHeight = (magnitude * canvasHeight).coerceAtLeast(2f)
        val top = canvasHeight - barHeight

        // Цвет меняется от низких частот (primary) к высоким (accent)
        val colorProgress = index.toFloat() / barCount
        val barColor = lerp(primaryColor, accentColor, colorProgress)

        // Рисуем столбик с вертикальным градиентом (снизу ярче, сверху темнее)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    barColor.copy(alpha = 0.5f),  // Верх — полупрозрачный
                    barColor                       // Низ — непрозрачный
                ),
                startY = top,
                endY = canvasHeight
            ),
            topLeft = Offset(left, top),
            size = Size(barWidth, barHeight)
        )

        // Рисуем маркер пика (маленький прямоугольник над столбиком)
        val peakY = canvasHeight - (peaks[index] * canvasHeight)
        if (peakY < top - 4.dp.toPx()) { // Показываем только если пик выше текущего столбика
            drawRect(
                color = barColor,
                topLeft = Offset(left, peakY),
                size = Size(barWidth, 3.dp.toPx())
            )
        }
    }
}

/**
 * Отрисовывает плавную волну через кубическую интерполяцию (Path + bezier).
 */
private fun DrawScope.drawWave(
    bars: List<Float>,
    primaryColor: Color,
    accentColor: Color
) {
    if (bars.isEmpty()) return

    val canvasWidth = size.width
    val canvasHeight = size.height
    val centerY = canvasHeight / 2f

    // Строим путь волны
    val wavePath = Path()
    val fillPath = Path()

    val step = canvasWidth / (bars.size - 1)

    // Начальная точка
    val startY = centerY - (bars[0] * centerY)
    wavePath.moveTo(0f, startY)
    fillPath.moveTo(0f, canvasHeight)
    fillPath.lineTo(0f, startY)

    // Кубические кривые Безье между точками — это даёт мягкость вместо острых углов
    for (i in 1 until bars.size) {
        val x = i * step
        val y = centerY - (bars[i] * centerY)

        val prevX = (i - 1) * step
        val prevY = centerY - (bars[i - 1] * centerY)

        // Контрольные точки для плавного перехода (1/3 и 2/3 между точками)
        val controlX1 = prevX + step / 3f
        val controlX2 = x - step / 3f

        wavePath.cubicTo(controlX1, prevY, controlX2, y, x, y)
        fillPath.cubicTo(controlX1, prevY, controlX2, y, x, y)
    }

    // Замыкаем путь заливки снизу
    fillPath.lineTo(canvasWidth, canvasHeight)
    fillPath.close()

    // Заливка под волной
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.6f),
                accentColor.copy(alpha = 0.1f)
            )
        )
    )

    // Линия волны
    drawPath(
        path = wavePath,
        brush = Brush.horizontalGradient(
            colors = listOf(primaryColor, accentColor)
        ),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    )
}

/**
 * Отрисовывает симметричные столбики: снизу и сверху от центра.
 * Красиво смотрится для визуализатора в стиле "зеркало".
 */
private fun DrawScope.drawMirrorBars(
    bars: List<Float>,
    primaryColor: Color,
    accentColor: Color
) {
    val barCount = bars.size
    if (barCount == 0) return

    val gapPx = 2.dp.toPx()
    val totalGap = gapPx * (barCount - 1)
    val barWidth = ((size.width - totalGap) / barCount).coerceAtLeast(1f)
    val centerY = size.height / 2f

    bars.forEachIndexed { index, magnitude ->
        val left = index * (barWidth + gapPx)
        val halfBarHeight = (magnitude * centerY).coerceAtLeast(2f)

        val colorProgress = index.toFloat() / barCount
        val barColor = lerp(primaryColor, accentColor, colorProgress)

        // Верхняя половина (отражение)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(barColor.copy(alpha = 0.3f), barColor),
                startY = centerY - halfBarHeight,
                endY = centerY
            ),
            topLeft = Offset(left, centerY - halfBarHeight),
            size = Size(barWidth, halfBarHeight)
        )

        // Нижняя половина
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(barColor, barColor.copy(alpha = 0.3f)),
                startY = centerY,
                endY = centerY + halfBarHeight
            ),
            topLeft = Offset(left, centerY),
            size = Size(barWidth, halfBarHeight)
        )
    }
}

// ── Утилиты ──────────────────────────────────────────────────────────────────

/**
 * Линейная интерполяция цвета между двумя значениями.
 * @param start Начальный цвет
 * @param end Конечный цвет
 * @param fraction Прогресс (0.0–1.0)
 */
private fun lerp(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f
    )
}
