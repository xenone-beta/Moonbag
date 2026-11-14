package dev.moonbag.app.ui.market

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.moonbag.app.R
import dev.moonbag.app.game.GlobalEvent
import dev.moonbag.app.game.GlobalEventKind

private fun title(kind: GlobalEventKind): Int = when (kind) {
    GlobalEventKind.BULL_RUN -> R.string.ev_bull_title
    GlobalEventKind.BEAR_MARKET -> R.string.ev_bear_title
    GlobalEventKind.MEME_SEASON -> R.string.ev_meme_title
    GlobalEventKind.FLASH_CRASH -> R.string.ev_crash_title
}

private fun body(kind: GlobalEventKind): Int = when (kind) {
    GlobalEventKind.BULL_RUN -> R.string.ev_bull_body
    GlobalEventKind.BEAR_MARKET -> R.string.ev_bear_body
    GlobalEventKind.MEME_SEASON -> R.string.ev_meme_body
    GlobalEventKind.FLASH_CRASH -> R.string.ev_crash_body
}

private fun icon(kind: GlobalEventKind): ImageVector = when (kind) {
    GlobalEventKind.BULL_RUN -> Icons.Filled.TrendingUp
    GlobalEventKind.BEAR_MARKET -> Icons.Filled.TrendingDown
    GlobalEventKind.MEME_SEASON -> Icons.Filled.Pets
    GlobalEventKind.FLASH_CRASH -> Icons.Filled.Warning
}

/** A card for the market-wide event that is running now, with a bar for the time left. */
@Composable
fun EventBanner(event: GlobalEvent) {
    val bad = event.kind == GlobalEventKind.BEAR_MARKET || event.kind == GlobalEventKind.FLASH_CRASH
    val progress by animateFloatAsState(event.progress.toFloat(), label = "event-progress")
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (bad) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon(event.kind), contentDescription = null)
                Text(
                    stringResource(title(event.kind)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(stringResource(body(event.kind)), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}
