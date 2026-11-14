package dev.moonbag.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import dev.moonbag.app.game.Candle
import dev.moonbag.app.ui.formatPrice
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.moonbag.app.CoinImages
import dev.moonbag.app.game.CoinDef

/** A line drawn over a chart (a moving average, say). Null entries are skipped. */
data class ChartOverlay(val values: List<Double?>, val color: Color)

private class Scale(val min: Double, val max: Double, val height: Float, padFraction: Float) {
    private val pad = height * padFraction
    private val range = (max - min).takeIf { it > 0 } ?: 1.0

    fun y(value: Double): Float = pad + (height - 2 * pad) * (1f - ((value - min) / range).toFloat())

    fun valueAt(y: Float): Double = min + (1.0 - ((y - pad) / (height - 2 * pad)).toDouble()) * range
}

private fun scaleFor(values: List<Double>, overlays: List<ChartOverlay>, height: Float): Scale {
    var lo = values.min()
    var hi = values.max()
    overlays.forEach { o ->
        o.values.forEach { v ->
            if (v != null) {
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
        }
    }
    return Scale(lo, hi, height, 0.08f)
}

private fun DrawScope.drawGrid(scale: Scale, width: Float, measurer: TextMeasurer, labelColor: Color) {
    val style = TextStyle(fontSize = 10.sp, color = labelColor)
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))
    for (fraction in listOf(0.2f, 0.5f, 0.8f)) {
        val y = scale.height * fraction
        drawLine(labelColor.copy(alpha = 0.25f), Offset(0f, y), Offset(width, y), 1.dp.toPx(), pathEffect = dash)
        val label = measurer.measure(formatPrice(scale.valueAt(y)), style)
        drawText(label, topLeft = Offset(width - label.size.width - 4.dp.toPx(), y - label.size.height - 1.dp.toPx()))
    }
}

private fun Modifier.scrubbing(count: Int, onScrub: ((Int?) -> Unit)?, set: (Int?) -> Unit): Modifier {
    if (onScrub == null || count < 1) return this
    return pointerInput(count) {
        fun indexAt(x: Float): Int = (x / size.width * count).toInt().coerceIn(0, count - 1)
        detectDragGestures(
            onDragStart = { offset -> indexAt(offset.x).let { set(it); onScrub(it) } },
            onDragEnd = { set(null); onScrub(null) },
            onDragCancel = { set(null); onScrub(null) },
            onDrag = { change, _ ->
                change.consume()
                indexAt(change.position.x).let { set(it); onScrub(it) }
            },
        )
    }
}

/** Small chart without axes, for list rows. */
@Composable
fun Sparkline(points: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val scale = Scale(points.min(), points.max(), size.height, 0.1f)
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = size.width * i / (points.size - 1)
            if (i == 0) path.moveTo(x, scale.y(v)) else path.lineTo(x, scale.y(v))
        }
        drawPath(path, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Line chart with a soft fill, optional indicator [overlays] and a price grid. With [onScrub] set,
 * dragging a finger over it reports the index of the nearest point (null on release).
 */
@Composable
fun LineChart(
    points: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    onScrub: ((Int?) -> Unit)? = null,
    strokeWidth: Dp = 2.dp,
    overlays: List<ChartOverlay> = emptyList(),
    grid: Boolean = false,
) {
    var scrub by remember { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier.scrubbing(points.size, onScrub) { scrub = it }) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val scale = scaleFor(points, overlays, h)
        if (grid) drawGrid(scale, w, measurer, labelColor)

        fun x(i: Int) = w * i / (points.size - 1)
        val line = Path()
        points.forEachIndexed { i, v -> if (i == 0) line.moveTo(x(i), scale.y(v)) else line.lineTo(x(i), scale.y(v)) }
        val fill = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawOverlays(overlays, points.size, scale) { i -> x(i) }

        val i = scrub
        if (i != null && i in points.indices) {
            val cx = x(i)
            val cy = scale.y(points[i])
            drawLine(color.copy(alpha = 0.5f), Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.dp.toPx())
            drawCircle(color, radius = 5.dp.toPx(), center = Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.dp.toPx(), center = Offset(cx, cy))
        }
    }
}

/** Candlestick chart. Green candles closed above their open, red ones below. */
@Composable
fun CandleChart(
    candles: List<Candle>,
    up: Color,
    down: Color,
    modifier: Modifier = Modifier,
    onScrub: ((Int?) -> Unit)? = null,
    overlays: List<ChartOverlay> = emptyList(),
    grid: Boolean = true,
) {
    var scrub by remember { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier.scrubbing(candles.size, onScrub) { scrub = it }) {
        if (candles.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val values = candles.flatMap { listOf(it.high, it.low) }
        val scale = scaleFor(values, overlays, h)
        if (grid) drawGrid(scale, w, measurer, labelColor)

        val slot = w / candles.size
        val bodyWidth = (slot * 0.62f).coerceAtLeast(1.5f)
        fun cx(i: Int) = slot * (i + 0.5f)

        val selected = scrub
        if (selected != null && selected in candles.indices) {
            drawRect(labelColor.copy(alpha = 0.12f), Offset(slot * selected, 0f), Size(slot, h))
        }
        candles.forEachIndexed { i, c ->
            val color = if (c.close >= c.open) up else down
            drawLine(color, Offset(cx(i), scale.y(c.high)), Offset(cx(i), scale.y(c.low)), strokeWidth = 1.4.dp.toPx())
            val top = scale.y(maxOf(c.open, c.close))
            val bottom = scale.y(minOf(c.open, c.close))
            drawRect(
                color,
                Offset(cx(i) - bodyWidth / 2, top),
                Size(bodyWidth, (bottom - top).coerceAtLeast(1.5.dp.toPx())),
            )
        }
        drawOverlays(overlays, candles.size, scale) { i -> cx(i) }
    }
}

private fun DrawScope.drawOverlays(overlays: List<ChartOverlay>, count: Int, scale: Scale, x: (Int) -> Float) {
    for (overlay in overlays) {
        val path = Path()
        var started = false
        for (i in 0 until minOf(count, overlay.values.size)) {
            val v = overlay.values[i] ?: continue
            if (!started) {
                path.moveTo(x(i), scale.y(v))
                started = true
            } else {
                path.lineTo(x(i), scale.y(v))
            }
        }
        if (started) {
            drawPath(path, overlay.color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** RSI strip: the 30 to 70 band is shaded, and the line turns to the accent colour outside it. */
@Composable
fun RsiPanel(values: List<Double?>, color: Color, modifier: Modifier = Modifier) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun y(v: Double) = h * (1f - (v / 100.0).toFloat())
        drawRect(labelColor.copy(alpha = 0.08f), Offset(0f, y(70.0)), Size(w, y(30.0) - y(70.0)))
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))
        for (level in listOf(30.0, 70.0)) {
            drawLine(labelColor.copy(alpha = 0.4f), Offset(0f, y(level)), Offset(w, y(level)), 1.dp.toPx(), pathEffect = dash)
        }
        if (values.size < 2) return@Canvas
        val path = Path()
        var started = false
        values.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val x = w * i / (values.size - 1)
            if (!started) {
                path.moveTo(x, y(v))
                started = true
            } else {
                path.lineTo(x, y(v))
            }
        }
        if (started) drawPath(path, color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Round coin badge: the photo you picked, a text glyph for the major coins, or an icon on a tint of the
 * coin's colour.
 */
@Composable
fun CoinAvatar(coin: CoinDef, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val tint = Color(coin.color)
    val context = LocalContext.current
    val photo = remember(coin.imageFile) { coin.imageFile?.let { CoinImages.load(context, it) } }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.40f), tint.copy(alpha = 0.14f)))),
        contentAlignment = Alignment.Center,
    ) {
        when {
            photo != null -> Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            coin.icon.startsWith("glyph:") -> Text(
                text = coin.icon.removePrefix("glyph:"),
                fontSize = (size.value * 0.5f).sp,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
            else -> Icon(
                imageVector = CoinIcons.get(coin.icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * 0.54f),
            )
        }
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
