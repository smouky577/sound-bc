package com.drbaysinger.audiobc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Small line icons drawn on a Canvas (no icon library needed). */
enum class Glyph { WIFI, PIN, TARGET, WAVE, BARS, BACK, HISTORY, DOWNLOAD, CHEVRON_DOWN, CHEVRON_UP, TRASH }

@Composable
fun Icon(glyph: Glyph, color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val s = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (glyph) {
            Glyph.WIFI -> wifi(color, s)
            Glyph.PIN -> pin(color, s)
            Glyph.TARGET -> {
                drawCircle(color, w * 0.42f, style = s)
                drawCircle(color, w * 0.18f)
            }
            Glyph.WAVE -> {
                val p = Path().apply {
                    moveTo(0.05f * w, 0.5f * w); lineTo(0.3f * w, 0.5f * w); lineTo(0.42f * w, 0.18f * w)
                    lineTo(0.58f * w, 0.82f * w); lineTo(0.7f * w, 0.5f * w); lineTo(0.95f * w, 0.5f * w)
                }
                drawPath(p, color, style = s)
            }
            Glyph.BARS -> {
                drawRoundRect(color, Offset(0.1f * w, 0.1f * w), Size(0.8f * w, 0.8f * w),
                    androidx.compose.ui.geometry.CornerRadius(0.15f * w), style = s)
                listOf(0.32f to 0.55f, 0.5f to 0.35f, 0.68f to 0.48f).forEach { (x, top) ->
                    drawLine(color, Offset(x * w, top * w), Offset(x * w, 0.72f * w), s.width, StrokeCap.Round)
                }
            }
            Glyph.BACK -> {
                val p = Path().apply { moveTo(0.62f * w, 0.18f * w); lineTo(0.3f * w, 0.5f * w); lineTo(0.62f * w, 0.82f * w) }
                drawPath(p, color, style = s)
            }
            Glyph.HISTORY -> {
                drawArc(color, -60f, 300f, false, Offset(0.12f * w, 0.12f * w), Size(0.76f * w, 0.76f * w), style = s)
                drawLine(color, Offset(0.5f * w, 0.3f * w), Offset(0.5f * w, 0.52f * w), s.width, StrokeCap.Round)
                drawLine(color, Offset(0.5f * w, 0.52f * w), Offset(0.64f * w, 0.62f * w), s.width, StrokeCap.Round)
            }
            Glyph.DOWNLOAD -> {
                drawLine(color, Offset(0.5f * w, 0.12f * w), Offset(0.5f * w, 0.64f * w), s.width, StrokeCap.Round)
                val p = Path().apply { moveTo(0.28f * w, 0.42f * w); lineTo(0.5f * w, 0.64f * w); lineTo(0.72f * w, 0.42f * w) }
                drawPath(p, color, style = s)
                drawLine(color, Offset(0.18f * w, 0.86f * w), Offset(0.82f * w, 0.86f * w), s.width, StrokeCap.Round)
            }
            Glyph.CHEVRON_DOWN, Glyph.CHEVRON_UP -> {
                val up = glyph == Glyph.CHEVRON_UP
                val y0 = if (up) 0.62f else 0.38f
                val y1 = if (up) 0.38f else 0.62f
                val p = Path().apply { moveTo(0.25f * w, y0 * w); lineTo(0.5f * w, y1 * w); lineTo(0.75f * w, y0 * w) }
                drawPath(p, color, style = s)
            }
            Glyph.TRASH -> {
                drawLine(color, Offset(0.15f * w, 0.25f * w), Offset(0.85f * w, 0.25f * w), s.width, StrokeCap.Round)
                drawLine(color, Offset(0.4f * w, 0.12f * w), Offset(0.6f * w, 0.12f * w), s.width, StrokeCap.Round)
                val p = Path().apply {
                    moveTo(0.25f * w, 0.25f * w); lineTo(0.3f * w, 0.88f * w); lineTo(0.7f * w, 0.88f * w); lineTo(0.75f * w, 0.25f * w)
                }
                drawPath(p, color, style = s)
            }
        }
    }
}

private fun DrawScope.wifi(color: Color, s: Stroke) {
    val w = size.width
    val c = Offset(0.5f * w, 0.82f * w)
    listOf(0.72f, 0.48f, 0.26f).forEach { r ->
        drawArc(color, 225f, 90f, false, Offset(c.x - r * w, c.y - r * w), Size(2 * r * w, 2 * r * w), style = s)
    }
    drawCircle(color, 0.07f * w, c)
}

private fun DrawScope.pin(color: Color, s: Stroke) {
    val w = size.width
    val p = Path().apply {
        moveTo(0.5f * w, 0.92f * w)
        cubicTo(0.2f * w, 0.6f * w, 0.15f * w, 0.45f * w, 0.15f * w, 0.4f * w)
        cubicTo(0.15f * w, 0.18f * w, 0.32f * w, 0.06f * w, 0.5f * w, 0.06f * w)
        cubicTo(0.68f * w, 0.06f * w, 0.85f * w, 0.18f * w, 0.85f * w, 0.4f * w)
        cubicTo(0.85f * w, 0.45f * w, 0.8f * w, 0.6f * w, 0.5f * w, 0.92f * w)
        close()
    }
    drawPath(p, color, style = s)
    drawCircle(color, 0.11f * w, Offset(0.5f * w, 0.4f * w), style = s)
}
