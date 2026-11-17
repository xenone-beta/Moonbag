package dev.moonbag.app.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.moonbag.app.R
import dev.moonbag.app.game.Chains
import dev.moonbag.app.game.GameState
import dev.moonbag.app.game.Nfts
import dev.moonbag.app.game.TxKind
import dev.moonbag.app.game.TradeSide
import dev.moonbag.app.game.Wallet
import dev.moonbag.app.game.WalletInfo
import dev.moonbag.app.ui.components.ChangeChip
import dev.moonbag.app.ui.components.CoinAvatar
import dev.moonbag.app.ui.components.FlashText
import dev.moonbag.app.ui.formatAmount
import dev.moonbag.app.ui.formatSignedUsd
import dev.moonbag.app.ui.formatTime
import dev.moonbag.app.ui.formatUsd
import dev.moonbag.app.ui.nft.NftArt
import dev.moonbag.app.ui.theme.MarketColors

/** One line of the activity list, whatever it came from. */
private data class Activity(val time: Long, val icon: ImageVector, val title: String, val detail: String, val amount: String, val positive: Boolean?)

@Composable
fun WalletScreen(
    state: GameState,
    wallet: WalletInfo,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onSwap: () -> Unit,
    onEarn: () -> Unit,
    onOpenCoin: (String) -> Unit,
    onAnalytics: () -> Unit,
    onSetup: () -> Unit,
    onBackup: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val net = state.netWorth()
    val profit = state.totalProfit()
    val clipboard = LocalClipboardManager.current
    val mainAddress = wallet.addresses[Chains.all.first().id].orEmpty()
    val held = state.holdings.keys
        .mapNotNull { state.coin(it) }
        .sortedByDescending { state.holdings.getValue(it.id).amount * state.price(it.id) }
    val activity = activityOf(state)

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (wallet.exists) Identicon(mainAddress, 40.dp) else Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.AccountBalanceWallet, null) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.main_wallet), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (wallet.exists) {
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable { clipboard.setText(AnnotatedString(mainAddress)) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Wallet.shorten(mainAddress), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.Filled.ContentCopy, null, Modifier.padding(start = 4.dp).size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                IconButton(onClick = onAnalytics) { Icon(Icons.Filled.ShowChart, stringResource(R.string.analytics)) }
            }
        }

        item(key = "balance") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FlashText(
                    text = formatUsd(net),
                    value = net,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        formatSignedUsd(profit),
                        style = MaterialTheme.typography.titleSmall,
                        color = MarketColors.forChange(profit),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.play_money),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "actions") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionButton(Icons.Filled.ArrowUpward, stringResource(R.string.send), Modifier.weight(1f), onSend)
                ActionButton(Icons.Filled.ArrowDownward, stringResource(R.string.receive), Modifier.weight(1f), onReceive)
                ActionButton(Icons.Filled.SwapHoriz, stringResource(R.string.swap), Modifier.weight(1f), onSwap)
                ActionButton(Icons.Filled.Bolt, stringResource(R.string.earn), Modifier.weight(1f), onEarn)
            }
        }

        if (!wallet.exists) {
            item(key = "setup") {
                Banner(
                    title = stringResource(R.string.create_wallet),
                    body = stringResource(R.string.no_wallet_body),
                    button = stringResource(R.string.create_wallet),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    onClick = onSetup,
                )
            }
        } else if (!wallet.backedUp) {
            item(key = "backup") {
                Banner(
                    title = stringResource(R.string.backup_banner_title),
                    body = stringResource(R.string.backup_banner_body),
                    button = stringResource(R.string.backup_now),
                    container = MaterialTheme.colorScheme.errorContainer,
                    onClick = onBackup,
                )
            }
        }

        item(key = "tabs") {
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tokens)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.nfts_tab)) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.activity)) })
            }
        }

        when (tab) {
            0 -> {
                item(key = "cash") {
                    TokenLine(
                        avatar = {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF2E7D32).copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                                Text("$", fontWeight = FontWeight.Bold, color = Color(0xFF43A047))
                            }
                        },
                        title = stringResource(R.string.usd_token),
                        subtitle = stringResource(R.string.usd_token_desc),
                        value = formatUsd(state.cash),
                        detail = null,
                        onClick = onEarn,
                    )
                }
                items(held, key = { "t-" + it.id }) { coin ->
                    val h = state.holdings.getValue(coin.id)
                    val change = state.change(coin.id)
                    TokenLine(
                        avatar = { CoinAvatar(coin) },
                        title = coin.name,
                        subtitle = coin.symbol + " · " + Chains.of(coin).name,
                        value = formatUsd(h.amount * state.price(coin.id)),
                        detail = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatAmount(h.amount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ChangeChip(change)
                        } },
                        onClick = { onOpenCoin(coin.id) },
                    )
                }
                if (held.isEmpty()) {
                    item(key = "no-tokens") { Hint(stringResource(R.string.tokens_empty)) }
                }
            }
            1 -> {
                if (state.nfts.isEmpty()) {
                    item(key = "no-nfts") { Hint(stringResource(R.string.nfts_empty)) }
                } else {
                    items(state.nfts.chunked(3), key = { row -> "n-" + row.first().id }) { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (nft in row) {
                                Column(Modifier.weight(1f)) {
                                    NftArt(nft, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)))
                                    Text(formatUsd(Nfts.value(state, nft)), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            else -> {
                if (activity.isEmpty()) {
                    item(key = "no-activity") { Hint(stringResource(R.string.activity_empty)) }
                }
                items(activity, key = { "a-" + it.time + it.title + it.amount }) { a ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) { Icon(a.icon, null, Modifier.size(20.dp)) }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(a.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                formatTime(a.time) + if (a.detail.isNotEmpty()) " · " + a.detail else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            a.amount,
                            style = MaterialTheme.typography.titleSmall,
                            color = when (a.positive) {
                                true -> MarketColors.up()
                                false -> MaterialTheme.colorScheme.onSurface
                                null -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun activityOf(state: GameState): List<Activity> {
    val out = ArrayList<Activity>()
    val sent = stringResource(R.string.tx_sent)
    val got = stringResource(R.string.tx_received)
    val airdrop = stringResource(R.string.tx_airdrop)
    val swapped = stringResource(R.string.tx_swap)
    val bought = stringResource(R.string.tx_bought)
    val sold = stringResource(R.string.tx_sold)
    for (t in state.walletTxs) {
        val coin = state.coin(t.coinId)?.symbol ?: t.coinId
        val other = state.coin(t.toCoinId)?.symbol ?: t.toCoinId
        when (t.kind) {
            TxKind.SEND -> out += Activity(t.time, Icons.Filled.ArrowUpward, sent.format(coin), Wallet.shorten(t.counterparty), "-" + formatAmount(t.amount), false)
            TxKind.RECEIVE -> out += Activity(t.time, Icons.Filled.ArrowDownward, got.format(coin), "", "+" + formatAmount(t.amount), true)
            TxKind.AIRDROP -> out += Activity(t.time, Icons.Filled.CardGiftcard, airdrop.format(coin), formatUsd(t.valueUsd), "+" + formatAmount(t.amount), true)
            TxKind.SWAP -> out += Activity(t.time, Icons.Filled.SwapHoriz, swapped.format(coin, other), formatUsd(t.valueUsd), "+" + formatAmount(t.toAmount), true)
        }
    }
    for (t in state.trades) {
        val coin = state.coin(t.coinId)?.symbol ?: t.coinId
        out += when (t.side) {
            TradeSide.BUY -> Activity(t.time, Icons.Filled.ShoppingCart, bought.format(coin), formatUsd(t.usd), "+" + formatAmount(t.amount), true)
            TradeSide.SELL -> Activity(t.time, Icons.Filled.Sell, sold.format(coin), formatUsd(t.usd), "-" + formatAmount(t.amount), false)
        }
    }
    return out.sortedByDescending { it.time }.take(60)
}

@Composable
private fun TokenLine(
    avatar: @Composable () -> Unit,
    title: String,
    subtitle: String,
    value: String,
    detail: (@Composable () -> Unit)?,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            detail?.invoke()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}

@Composable
private fun Banner(title: String, body: String, button: String, container: Color, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onClick) { Text(button) }
        }
    }
}
