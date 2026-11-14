package dev.moonbag.app.ui.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.moonbag.app.R
import dev.moonbag.app.game.CoinDef
import dev.moonbag.app.game.Holding
import dev.moonbag.app.game.Staking
import dev.moonbag.app.ui.components.Stat
import dev.moonbag.app.ui.formatAmount
import dev.moonbag.app.ui.formatPercent

/** Shows what is locked, the reward rate, and buttons to lock or free coins. */
@Composable
fun StakingCard(coin: CoinDef, holding: Holding, onStake: () -> Unit, onUnstake: () -> Unit) {
    val rate = Staking.ratePerHour(coin)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Text(
                    stringResource(R.string.staking),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Stat(stringResource(R.string.staked), "${formatAmount(holding.staked)} ${coin.symbol}", Modifier.weight(1f))
                Stat(stringResource(R.string.reward_rate), stringResource(R.string.per_hour_pct, formatPercent(rate)), Modifier.weight(1f))
            }
            Text(
                stringResource(R.string.staking_note),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStake, enabled = holding.liquid > 0.0, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.stake))
                }
                OutlinedButton(onClick = onUnstake, enabled = holding.staked > 0.0, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.unstake))
                }
            }
        }
    }
}

@Composable
fun StakeDialog(
    coin: CoinDef,
    staking: Boolean,
    maxAmount: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var fraction by remember { mutableFloatStateOf(1f) }
    val amount = if (fraction >= 0.999f) maxAmount else maxAmount * fraction
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (staking) R.string.stake_title else R.string.unstake_title, coin.symbol)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${formatAmount(amount)} ${coin.symbol}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Slider(value = fraction, onValueChange = { fraction = it }, valueRange = 0.01f..1f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.25f, 0.5f, 0.75f, 1f).forEach { f ->
                        FilterChip(
                            selected = fraction == f,
                            onClick = { fraction = f },
                            label = { Text("${(f * 100).toInt()}%") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onConfirm(amount) }, enabled = amount > 0.0) {
                Text(stringResource(if (staking) R.string.stake else R.string.unstake))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
