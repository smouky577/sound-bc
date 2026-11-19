package com.drbaysinger.audiobc.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drbaysinger.audiobc.audio.Spectrogram
import com.drbaysinger.audiobc.audio.median
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Waveform (envelope bars, SHOT blue / GONG amber) or log-frequency spectrogram of one shot,
 * over its view window. With [interactive] the SHOT and GONG markers can be dragged; a tap
 * places a marker that was not detected.
 */
@Composable
fun ShotChart(
    shot: ShotUi,
    spectrogram: Spectrogram?,
    showSpectrogram: Boolean,
    interactive: Boolean,
    modifier: Modifier,
    onMove: (which: Int, time: Double) -> Unit = { _, _ -> },
) {
    val measurer = rememberTextMeasurer()
    val shotNow by rememberUpdatedState(shot)
    val moveNow by rememberUpdatedState(onMove)
    val bitmap = remember(spectrogram) { spectrogram?.let(::spectrogramBitmap) }
    val noise = remember(shot.envelope) { median(shot.envelope).coerceAtLeast(1e-7) }
    Canvas(
        modifier.then(
            if (!interactive) Modifier else Modifier.pointerInput(shot.number, shot.name) {
                val slop = 28.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val s = shotNow
                    val span = (s.viewEnd - s.viewStart).coerceAtLeast(1e-6)
                    fun xOf(t: Double) = ((t - s.viewStart) / span * size.width).toFloat()
                    fun tOf(x: Float) = (s.viewStart + x / size.width.coerceAtLeast(1) * span)
                    val d1 = if (s.muzzleFound) abs(down.position.x - xOf(s.t1)) else Float.MAX_VALUE
                    val d2 = if (s.gongFound) abs(down.position.x - xOf(s.t2)) else Float.MAX_VALUE
                    val which = when {
                        d1 <= slop && d1 <= d2 -> 1
                        d2 <= slop -> 2
                        else -> 0
                    }
                    if (which != 0) {
                        down.consume()
                        drag(down.id) { change ->
                            moveNow(which, tOf(change.position.x.coerceIn(0f, size.width.toFloat())))
                            change.consume()
                        }
                    } else {
                        // Tap: place a marker that is missing.
                        var moved = false
                        var up = down
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            if ((c.position - down.position).getDistance() > slop / 2) moved = true
                            up = c
                            if (!c.pressed) break
                        }
                        if (!moved) {
                            val t = tOf(up.position.x)
                            when {
                                !s.muzzleFound -> moveNow(1, t)
                                !s.gongFound -> moveNow(2, t)
                            }
                        }
                    }
                }
            },
        ),
    ) {
        drawRoundRect(Bg, cornerRadius = CornerRadius(10.dp.toPx()))
        val s = shot
        val span = (s.viewEnd - s.viewStart)
        if (span <= 0 || s.envelope.isEmpty()) return@Canvas
        fun xOf(t: Double) = ((t - s.viewStart) / span * size.width).toFloat()
        if (showSpectrogram && bitmap != null) {
            drawImage(
                bitmap,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        } else if (!showSpectrogram) {
            drawWave(s, noise)
        }
        if (interactive) {
            if (s.muzzleFound) marker(xOf(s.t1), Blue, "SHOT", measurer)
            if (s.gongFound) marker(xOf(s.t2), Accent, "GONG", measurer)
        } else if (showSpectrogram) {
            if (s.muzzleFound) drawLine(Blue, Offset(xOf(s.t1), 0f), Offset(xOf(s.t1), size.height), 2.dp.toPx())
            if (s.gongFound) drawLine(Accent, Offset(xOf(s.t2), 0f), Offset(xOf(s.t2), size.height), 2.dp.toPx())
        }
    }
}

/** Live rolling trace while recording: a flat line near the bottom with impulses going up. */
@Composable
fun LiveChart(tail: FloatArray, threshold: Float, modifier: Modifier) {
    Canvas(modifier) {
        drawRoundRect(Bg, cornerRadius = CornerRadius(10.dp.toPx()))
        if (tail.isEmpty()) return@Canvas
        val noise = median(tail).coerceAtLeast(1e-7)
        val peak = (tail.maxOrNull() ?: 0f).toDouble().coerceAtLeast(threshold.toDouble() * 2).coerceAtLeast(noise * 10)
        val points = (size.width / 2.dp.toPx()).toInt().coerceAtLeast(2)
        val window = 1500 // rolling 1.5 s at 1 ms per envelope sample
        val per = window.toDouble() / points
        val start = tail.size - window
        drawTrace(points) { k ->
            val i0 = start + (k * per).toInt()
            val i1 = start + ((k + 1) * per).toInt().coerceAtLeast((k * per).toInt() + 1)
            var m = -1f
            for (i in maxOf(i0, 0) until minOf(i1, tail.size)) if (tail[i] > m) m = tail[i]
            if (m < 0f) null else level(m.toDouble(), noise, peak) to (if (m >= threshold) Danger else Grey)
        }
    }
}

/** Rolling lava spectrogram while recording. */
@Composable
fun LiveSpectrogram(spectrogram: Spectrogram, modifier: Modifier) {
    val bitmap = remember(spectrogram) { spectrogramBitmap(spectrogram) }
    Canvas(modifier) {
        drawRoundRect(Bg, cornerRadius = CornerRadius(10.dp.toPx()))
        drawImage(bitmap, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Low)
    }
}

private fun level(v: Double, noise: Double, peak: Double): Float {
    val top = 20 * log10(peak / noise)
    if (top <= 0) return 0f
    return (20 * log10((v / noise).coerceAtLeast(1.0)) / top).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Line trace: baseline near the bottom, each point rises by its level (0..1). [sample] returns the level
 * and colour of point k, or null where there is no data yet.
 */
private fun DrawScope.drawTrace(points: Int, sample: (Int) -> Pair<Float, Color>?) {
    val base = size.height - 8.dp.toPx()
    val top = 30.dp.toPx().coerceAtMost(size.height * 0.3f)
    val range = base - top
    val stroke = 1.6.dp.toPx()
    val step = size.width / (points - 1)
    var prev: Offset? = null
    for (k in 0 until points) {
        val p = sample(k)
        if (p == null) { prev = null; continue }
        val pt = Offset(k * step, base - p.first * range)
        prev?.let { drawLine(p.second, it, pt, stroke, StrokeCap.Round) }
        prev = pt
    }
}

private fun DrawScope.drawWave(s: ShotUi, noise: Double) {
    val rate = s.sampleRate
    val env = s.envelope
    val a = (s.viewStart * rate).toInt().coerceIn(0, env.size)
    val b = (s.viewEnd * rate).toInt().coerceIn(a, env.size)
    if (b <= a) return
    var peak = noise * 10
    for (i in a until b) if (env[i] > peak) peak = env[i].toDouble()
    val shotEnd = burstEnd(s, s.t1)
    val gongEnd = burstEnd(s, s.t2)
    val points = (size.width / 1.5.dp.toPx()).toInt().coerceIn(2, b - a)
    val per = (b - a).toDouble() / points
    drawTrace(points) { k ->
        val i0 = a + (k * per).toInt()
        val i1 = (a + ((k + 1) * per).toInt()).coerceAtMost(b).coerceAtLeast(i0 + 1)
        var m = 0f
        for (i in i0 until i1) if (env[i] > m) m = env[i]
        val t = (i0 + i1) / 2.0 / rate
        val color = when {
            s.muzzleFound && t >= s.t1 - per / rate && t <= shotEnd -> Blue
            s.gongFound && t >= s.t2 - per / rate && t <= gongEnd -> Accent
            else -> Grey
        }
        level(m.toDouble(), noise, peak) to color
    }
}

/** End of the loud burst that starts at [t0]: envelope back under the threshold for 10 ms (max 120 ms). */
private fun burstEnd(s: ShotUi, t0: Double): Double {
    val rate = s.sampleRate
    val env = s.envelope
    val start = (t0 * rate).toInt().coerceIn(0, env.size)
    val max = (start + 0.12 * rate).toInt().coerceAtMost(env.size)
    val need = (0.01 * rate).toInt()
    var quiet = 0
    for (i in start until max) {
        if (env[i] < s.threshold) quiet++ else quiet = 0
        if (quiet >= need && i - start > need) return (i - need).toDouble() / rate
    }
    return max.toDouble() / rate
}

private fun DrawScope.marker(x: Float, color: Color, label: String, measurer: androidx.compose.ui.text.TextMeasurer) {
    val xx = x.coerceIn(0f, size.width)
    drawLine(color, Offset(xx, 0f), Offset(xx, size.height), 3.dp.toPx())
    val layout = measurer.measure(label, TextStyle(color = OnAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold))
    val padH = 6.dp.toPx()
    val padV = 3.dp.toPx()
    val w = layout.size.width + 2 * padH
    val h = layout.size.height + 2 * padV
    val left = (xx - w / 2).coerceIn(0f, size.width - w)
    drawRoundRect(color, Offset(left, 6.dp.toPx()), Size(w, h), CornerRadius(h / 2))
    drawText(layout, topLeft = Offset(left + padH, 6.dp.toPx() + padV))
}

private fun spectrogramBitmap(s: Spectrogram): ImageBitmap {
    val bmp = Bitmap.createBitmap(s.cols, s.rows, Bitmap.Config.ARGB_8888)
    val px = IntArray(s.cols * s.rows)
    for (c in 0 until s.cols) {
        for (r in 0 until s.rows) {
            val v = s.data[c * s.rows + r]
            px[(s.rows - 1 - r) * s.cols + c] = colormap(v)
        }
    }
    bmp.setPixels(px, 0, s.cols, 0, 0, s.cols, s.rows)
    return bmp.asImageBitmap()
}

/** "Lava" (inferno): black → purple → red → orange → yellow → pale white. */
private val LAVA = arrayOf(
    floatArrayOf(0f, 0f, 0f, 4f),
    floatArrayOf(0.13f, 31f, 12f, 72f),
    floatArrayOf(0.25f, 85f, 15f, 109f),
    floatArrayOf(0.38f, 136f, 34f, 106f),
    floatArrayOf(0.5f, 186f, 54f, 85f),
    floatArrayOf(0.63f, 227f, 89f, 51f),
    floatArrayOf(0.75f, 249f, 140f, 10f),
    floatArrayOf(0.88f, 249f, 201f, 50f),
    floatArrayOf(1f, 252f, 255f, 164f),
)

private fun colormap(v: Float): Int {
    val stops = LAVA
    val x = v.coerceIn(0f, 1f)
    var i = 0
    while (i < stops.size - 2 && x > stops[i + 1][0]) i++
    val a = stops[i]
    val b = stops[i + 1]
    val f = ((x - a[0]) / (b[0] - a[0])).coerceIn(0f, 1f)
    fun ch(k: Int) = (a[k] + (b[k] - a[k]) * f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(1) shl 16) or (ch(2) shl 8) or ch(3)
}
