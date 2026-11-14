package dev.moonbag.app.ui.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.moonbag.app.R
import dev.moonbag.app.game.CampaignKind
import dev.moonbag.app.game.CoinDef
import dev.moonbag.app.game.CoinManagement
import dev.moonbag.app.game.GameState
import dev.moonbag.app.game.MarketingTier
import dev.moonbag.app.ui.components.Stat
import dev.moonbag.app.ui.formatAmount
import dev.moonbag.app.ui.formatUsd

private fun tierName(tier: MarketingTier): Int = when (tier) {
    MarketingTier.SMALL -> R.string.tier_small
    MarketingTier.MEDIUM -> R.string.tier_medium
    MarketingTier.BIG -> R.string.tier_big
}

/** Marketing campaigns and token burns for a coin you created. */
@Composable
fun ManageCoinCard(
    coin: CoinDef,
    state: GameState,
    onMarketing: (MarketingTier) -> Unit,
    onBurn: () -> Unit,
) {
    val custom = state.customCoins.firstOrNull { it.id == coin.id } ?: return
    val running = state.campaigns.filter { it.coinId == coin.id }
    val marketing = running.firstOrNull { it.kind == CampaignKind.MARKETING }
    val liquid = state.holdings[coin.id]?.liquid ?: 0.0

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.manage_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(Modifier.fillMaxWidth()) {
                Stat(stringResource(R.string.burned), formatAmount(custom.burned), Modifier.weight(1f))
                Stat(stringResource(R.string.supply), formatAmount(coin.supply), Modifier.weight(1f))
            }
            Text(stringResource(R.string.manage_note), style = MaterialTheme.typography.bodySmall)

            if (running.isNotEmpty()) {
                val top = running.maxByOrNull { it.progress }
                Text(
                    stringResource(if (marketing != null) R.string.campaign_running else R.string.burn_hype_running),
                    style = MaterialTheme.typography.labelLarge,
                )
                LinearProgressIndicator(progress = { (top?.progress ?: 0.0).toFloat() }, modifier = Modifier.fillMaxWidth())
            }

            Text(stringResource(R.string.marketing), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MarketingTier.entries.forEach { tier ->
                    FilledTonalButton(
                        onClick = { onMarketing(tier) },
                        enabled = marketing == null && state.cash + 1e-9 >= tier.cost,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Text(stringResource(tierName(tier)), style = MaterialTheme.typography.labelLarge)
                            Text(formatUsd(tier.cost), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            OutlinedButton(onClick = onBurn, enabled = liquid > 0.0, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.burn_coins))
            }
        }
    }
}

@Composable
fun BurnDialog(
    coin: CoinDef,
    state: GameState,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    val liquid = state.holdings[coin.id]?.liquid ?: 0.0
    var fraction by remember { mutableFloatStateOf(0.25f) }
    val amount = liquid * if (fraction >= 0.999f) 1.0 else fraction.toDouble()
    val value = amount * state.price(coin.id)
    val hype = CoinManagement.burnStrength(state, coin.id, amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.burn_title, coin.symbol)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${formatAmount(amount)} ${coin.symbol}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.burn_cost, formatUsd(value), "%.1f".format(hype)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(value = fraction, onValueChange = { fraction = it }, valueRange = 0.01f..1f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.1f, 0.25f, 0.5f, 1f).forEach { f ->
                        FilterChip(selected = fraction == f, onClick = { fraction = f }, label = { Text("${(f * 100).toInt()}%") })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(amount) }, enabled = amount > 0.0) { Text(stringResource(R.string.burn_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
