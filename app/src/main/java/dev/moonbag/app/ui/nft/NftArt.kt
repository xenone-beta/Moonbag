package dev.moonbag.app.ui.nft

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import dev.moonbag.app.game.Nft
import dev.moonbag.app.game.Rarity
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun rarityColor(rarity: Rarity): Color = when (rarity) {
    Rarity.COMMON -> Color(0xFF90A4AE)
    Rarity.RARE -> Color(0xFF42A5F5)
    Rarity.EPIC -> Color(0xFFAB47BC)
    Rarity.LEGENDARY -> Color(0xFFFFC107)
}

private fun hue(h: Float, s: Float, v: Float): Color = Color.hsv(((h % 360f) + 360f) % 360f, s, v)

/** A piece of art drawn from the NFT's seed, inside a frame coloured by its rarity. */
@Composable
fun NftArt(nft: Nft, modifier: Modifier = Modifier) {
    val frame = rarityColor(nft.rarity)
    val width = if (nft.rarity == Rarity.COMMON) 1.dp else 3.dp
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .border(width, frame, RoundedCornerShape(16.dp)),
    ) {
        Canvas(Modifier.matchParentSize()) {
            when (nft.collection) {
                "pixel" -> drawPixel(nft.seed)
                "orbs" -> drawOrbs(nft.seed)
                else -> drawRock(nft.seed)
            }
            if (nft.rarity == Rarity.LEGENDARY) drawSparkles(nft.seed)
        }
    }
}

private fun DrawScope.drawPixel(seed: Int) {
    val r = Random(seed.toLong())
    val base = r.nextFloat() * 360f
    drawRect(Brush.verticalGradient(listOf(hue(base + 180f, 0.35f, 0.30f), hue(base + 200f, 0.45f, 0.18f))))
    val n = 8
    val cell = size.minDimension / (n + 2)
    val origin = Offset((size.width - cell * n) / 2, (size.height - cell * n) / 2)
    val body = hue(base, 0.65f, 0.95f)
    val accent = hue(base + 40f, 0.7f, 1f)
    for (y in 0 until n) {
        for (x in 0 until n / 2) {
            if (r.nextFloat() < 0.55f) {
                val color = if (r.nextFloat() < 0.2f) accent else body
                for (cx in listOf(x, n - 1 - x)) {
                    drawRect(color, origin + Offset(cx * cell, y * cell), Size(cell, cell))
                }
            }
        }
    }
}

private fun DrawScope.drawOrbs(seed: Int) {
    val r = Random(seed.toLong())
    val base = r.nextFloat() * 360f
    drawRect(Brush.radialGradient(listOf(hue(base + 150f, 0.5f, 0.28f), Color(0xFF0B0B14)), center, size.maxDimension))
    val count = 3 + r.nextInt(4)
    repeat(count) {
        val radius = size.minDimension * (0.10f + r.nextFloat() * 0.20f)
        val c = Offset(
            size.width * (0.25f + r.nextFloat() * 0.5f),
            size.height * (0.25f + r.nextFloat() * 0.5f),
        )
        val h = base + r.nextFloat() * 90f
        drawCircle(
            Brush.radialGradient(
                listOf(hue(h, 0.25f, 1f), hue(h, 0.8f, 0.9f), hue(h + 20f, 0.9f, 0.45f)),
                center = c - Offset(radius * 0.3f, radius * 0.3f),
                radius = radius * 1.4f,
            ),
            radius,
            c,
        )
    }
}

private fun DrawScope.drawRock(seed: Int) {
    val r = Random(seed.toLong())
    val base = 20f + r.nextFloat() * 40f
    drawRect(Brush.verticalGradient(listOf(hue(base + 200f, 0.25f, 0.35f), hue(base + 210f, 0.3f, 0.15f))))
    val c = center
    val points = 9
    val outline = List(points) { i ->
        val angle = (2 * PI * i / points).toFloat()
        val radius = size.minDimension * (0.26f + r.nextFloat() * 0.16f)
        c + Offset(cos(angle) * radius, sin(angle) * radius)
    }
    for (i in 0 until points) {
        val a = outline[i]
        val b = outline[(i + 1) % points]
        val shade = 0.35f + r.nextFloat() * 0.5f
        val path = Path().apply {
            moveTo(c.x, c.y)
            lineTo(a.x, a.y)
            lineTo(b.x, b.y)
            close()
        }
        drawPath(path, hue(base, 0.25f, shade))
    }
}

private fun DrawScope.drawSparkles(seed: Int) {
    val r = Random(seed.toLong() xor 0x5EED)
    repeat(6) {
        val c = Offset(size.width * r.nextFloat(), size.height * r.nextFloat())
        val s = size.minDimension * (0.02f + r.nextFloat() * 0.03f)
        drawLine(Color(0xFFFFF3B0), c - Offset(s * 2, 0f), c + Offset(s * 2, 0f), strokeWidth = s * 0.6f)
        drawLine(Color(0xFFFFF3B0), c - Offset(0f, s * 2), c + Offset(0f, s * 2), strokeWidth = s * 0.6f)
    }
}
