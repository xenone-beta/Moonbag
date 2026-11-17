@file:OptIn(ExperimentalMaterial3Api::class)

package dev.moonbag.app.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.moonbag.app.R
import dev.moonbag.app.game.Chain
import dev.moonbag.app.game.Chains
import dev.moonbag.app.game.CoinDef
import dev.moonbag.app.game.GameState
import dev.moonbag.app.ui.components.CoinAvatar
import dev.moonbag.app.ui.formatAmount
import java.security.MessageDigest

/** A round picture drawn from an address, the way wallets show an account. */
@Composable
fun Identicon(seed: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val hash = androidx.compose.runtime.remember(seed) { MessageDigest.getInstance("SHA-256").digest(seed.toByteArray()) }
    val hue = (hash[0].toInt() and 0xFF) * 360f / 256f
    val ink = Color.hsv(hue, 0.55f, 0.90f)
    Canvas(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.hsv(hue, 0.35f, 0.22f)),
    ) {
        val cell = this.size.width / 7f
        for (row in 0 until 5) {
            for (col in 0 until 3) {
                if ((hash[1 + row * 3 + col].toInt() and 1) == 1) {
                    for (x in listOf(col, 4 - col).distinct()) {
                        drawRect(ink, Offset((x + 1) * cell, (row + 1) * cell), Size(cell, cell))
                    }
                }
            }
        }
    }
}

@Composable
fun ChainBadge(chain: Chain, modifier: Modifier = Modifier) {
    val tint = Color(chain.color)
    Text(
        chain.name,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = tint,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A round action button with its label underneath: Send, Receive, Swap. */
@Composable
fun ActionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun WalletTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        },
    )
}

/** Parses what the user typed as an amount; both "1.5" and "1,5" work. */
fun parseAmount(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

/** A list of coins to pick from. [balances] show on the right when the coin is held. */
@Composable
fun CoinPickerDialog(
    title: String,
    state: GameState,
    coins: List<CoinDef>,
    onPick: (CoinDef) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (coins.isEmpty()) {
                Text(stringResource(R.string.nothing_here))
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(coins, key = { it.id }) { coin ->
                        val held = state.holdings[coin.id]
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(coin) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoinAvatar(coin, size = 36.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(coin.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(coin.symbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    ChainBadge(Chains.of(coin))
                                }
                            }
                            if (held != null) Text(formatAmount(held.liquid), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
