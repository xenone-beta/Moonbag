package dev.moonbag.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.moonbag.app.ui.formatPercent
import dev.moonbag.app.ui.theme.MarketColors
import kotlinx.coroutines.delay

/** Percent change in a tinted pill. */
@Composable
fun ChangeChip(change: Double, modifier: Modifier = Modifier) {
    val color = MarketColors.forChange(change)
    Text(
        text = formatPercent(change),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Text that flashes green or red for a moment whenever [value] goes up or down. */
@Composable
fun FlashText(
    text: String,
    value: Double,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    var previous by remember { mutableStateOf(value) }
    var direction by remember { mutableIntStateOf(0) }
    LaunchedEffect(value) {
        direction = when {
            value > previous -> 1
            value < previous -> -1
            else -> 0
        }
        previous = value
        if (direction != 0) {
            delay(600)
            direction = 0
        }
    }
    val up = MarketColors.up()
    val down = MarketColors.down()
    val target: Color = when (direction) {
        1 -> up
        -1 -> down
        else -> baseColor
    }
    val color by animateColorAsState(target, tween(if (direction == 0) 500 else 120), label = "flash")
    Text(text = text, style = style, color = color, modifier = modifier, fontWeight = fontWeight)
}

/** Label above a value; used for small stat blocks. */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
    }
}
