package dev.moonbag.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.moonbag.app.game.CoinDef
import kotlin.math.roundToInt

private fun linePath(points: List<Double>, width: Float, height: Float, padFraction: Float): Path {
    val path = Path()
    if (points.size < 2) return path
    val min = points.min()
    val max = points.max()
    val range = (max - min).takeIf { it > 0 } ?: 1.0
    val pad = height * padFraction
    val usable = height - 2 * pad
    points.forEachIndexed { i, v ->
        val x = width * i / (points.size - 1)
        val y = pad + usable * (1f - ((v - min) / range).toFloat())
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

/** Small chart without axes, for list rows. */
@Composable
fun Sparkline(points: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = linePath(points, size.width, size.height, 0.1f)
        drawPath(path, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Line chart with a soft fill. With [onScrub] set, dragging a finger over it reports the index of
 * the nearest point (and null on release), and a marker follows the finger.
 */
@Composable
fun LineChart(
    points: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    onScrub: ((Int?) -> Unit)? = null,
    strokeWidth: Dp = 2.dp,
) {
    var scrub by remember { mutableStateOf<Int?>(null) }
    val gestures = if (onScrub != null && points.size >= 2) {
        Modifier.pointerInput(points.size) {
            fun indexAt(x: Float): Int =
                (x / size.width * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
            detectDragGestures(
                onDragStart = { offset -> scrub = indexAt(offset.x).also { onScrub(it) } },
                onDragEnd = { scrub = null; onScrub(null) },
                onDragCancel = { scrub = null; onScrub(null) },
                onDrag = { change, _ ->
                    change.consume()
                    scrub = indexAt(change.position.x).also { onScrub(it) }
                },
            )
        }
    } else {
        Modifier
    }

    Canvas(modifier.then(gestures)) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val line = linePath(points, w, h, 0.08f)
        val fill = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val i = scrub
        if (i != null && i in points.indices) {
            val min = points.min()
            val range = (points.max() - min).takeIf { it > 0 } ?: 1.0
            val pad = h * 0.08f
            val x = w * i / (points.size - 1)
            val y = pad + (h - 2 * pad) * (1f - ((points[i] - min) / range).toFloat())
            drawLine(color.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
            drawCircle(color, radius = 5.dp.toPx(), center = Offset(x, y))
            drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.dp.toPx(), center = Offset(x, y))
        }
    }
}

/** Round avatar with the coin's emoji on a tint of its colour. */
@Composable
fun CoinAvatar(coin: CoinDef, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val tint = Color(coin.color)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.35f), tint.copy(alpha = 0.12f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = coin.emoji, fontSize = (size.value * 0.5f).sp)
    }
}

/** A horizontal bar split into coloured parts; [parts] are (weight, colour). */
@Composable
fun AllocationBar(parts: List<Pair<Double, Color>>, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.first }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (total > 0) {
            parts.filter { it.first > 0 }.forEach { (weight, color) ->
                Box(
                    Modifier
                        .weight((weight / total).toFloat().coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(color),
                )
            }
        }
    }
}
