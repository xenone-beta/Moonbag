@file:OptIn(ExperimentalMaterial3Api::class)

package dev.moonbag.app.ui.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.moonbag.app.R
import dev.moonbag.app.game.GameState
import dev.moonbag.app.game.Rivals
import dev.moonbag.app.ui.market.OrderRow
import dev.moonbag.app.game.TradeSide
import dev.moonbag.app.ui.components.AllocationBar
import dev.moonbag.app.ui.components.ChangeChip
import dev.moonbag.app.ui.components.CoinAvatar
import dev.moonbag.app.ui.components.FlashText
import dev.moonbag.app.ui.components.LineChart
import dev.moonbag.app.ui.components.Stat
import dev.moonbag.app.ui.formatAmount
import dev.moonbag.app.ui.formatSignedUsd
import dev.moonbag.app.ui.formatTime
import dev.moonbag.app.ui.formatUsd
import dev.moonbag.app.ui.theme.MarketColors

@Composable
fun PortfolioScreen(
    state: GameState,
    onOpenCoin: (String) -> Unit,
    onGoMarket: () -> Unit,
    onOpenBoard: () -> Unit,
    onCancelOrder: (Long) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val net = state.netWorth()
    val profit = state.totalProfit()
    val held = state.holdings.keys
        .mapNotNull { state.coin(it) }
        .sortedByDescending { state.holdings.getValue(it.id).amount * state.price(it.id) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_portfolio), fontWeight = FontWeight.Bold) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "net") {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.net_worth),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                        FlashText(
                            text = formatUsd(net),
                            value = net,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            baseColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        val chart = state.netHistory.takeLast(150)
                        val up = chart.size < 2 || chart.last() >= chart.first()
                        LineChart(
                            points = chart,
                            color = if (up) MarketColors.up() else MarketColors.down(),
                            modifier = Modifier.fillMaxWidth().height(110.dp),
                        )
                    }
                }
            }

            item(key = "rank") {
                val rank = Rivals.rank(state, System.currentTimeMillis())
                Card(
                    onClick = onOpenBoard,
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                stringResource(R.string.rank_title, rank, Rivals.all.size + 1),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(stringResource(R.string.rank_open), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (state.orders.isNotEmpty()) {
                item(key = "orders-title") {
                    Text(
                        stringResource(R.string.orders),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(state.orders, key = { "o-" + it.id }) { order ->
                    val coin = state.coin(order.coinId) ?: return@items
                    Card(
                        onClick = { onOpenCoin(coin.id) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            OrderRow(order, coin, onCancel = { onCancelOrder(order.id) })
                        }
                    }
                }
            }

            item(key = "stats") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Stat(stringResource(R.string.nft_value), formatUsd(state.nftValue()), Modifier.weight(1f))
                            Stat(stringResource(R.string.staking_earned), formatUsd(state.stakingEarned), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth()) {
                            Stat(stringResource(R.string.cash), formatUsd(state.cash), Modifier.weight(1f))
                            Stat(stringResource(R.string.invested), formatUsd(state.lifetimeEarned), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth()) {
                            Stat(
                                stringResource(R.string.total_profit),
                                formatSignedUsd(profit),
                                Modifier.weight(1f),
                                valueColor = MarketColors.forChange(profit),
                            )
                            Stat(
                                stringResource(R.string.realized),
                                formatSignedUsd(state.realizedPnl),
                                Modifier.weight(1f),
                                valueColor = MarketColors.forChange(state.realizedPnl),
                            )
                        }
                        if (net > 0) {
                            AllocationBar(
                                buildList {
                                    add(state.cash to MaterialTheme.colorScheme.outline)
                                    held.forEach { c ->
                                        add(state.holdings.getValue(c.id).amount * state.price(c.id) to Color(c.color))
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (held.isEmpty()) {
                item(key = "empty") {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.no_coins_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(stringResource(R.string.no_coins_body), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onGoMarket) { Text(stringResource(R.string.open_market)) }
                        }
                    }
                }
            } else {
                item(key = "holdings-title") {
                    Text(
                        stringResource(R.string.your_coins),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(held, key = { "h-" + it.id }) { coin ->
                    val h = state.holdings.getValue(coin.id)
                    val value = h.amount * state.price(coin.id)
                    val pnlPct = if (h.costBasis > 0) value / h.costBasis - 1.0 else 0.0
                    Card(
                        onClick = { onOpenCoin(coin.id) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth().animateItem(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoinAvatar(coin)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(coin.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(
                                    "${formatAmount(h.amount)} ${coin.symbol}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    formatUsd(value),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                ChangeChip(pnlPct)
                            }
                        }
                    }
                }
            }

            if (state.trades.isNotEmpty()) {
                item(key = "trades-title") {
                    Text(
                        stringResource(R.string.history),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(state.trades.take(10), key = { "t-" + it.time + it.coinId + it.side.name + it.amount }) { t ->
                    val coin = state.coin(t.coinId) ?: return@items
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (t.side == TradeSide.BUY) MarketColors.up() else MarketColors.down()),
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                stringResource(
                                    if (t.side == TradeSide.BUY) R.string.trade_line_buy else R.string.trade_line_sell,
                                    formatAmount(t.amount),
                                    coin.symbol,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                formatTime(t.time),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            if (t.side == TradeSide.SELL) formatSignedUsd(t.pnl) else formatUsd(t.usd),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (t.side == TradeSide.SELL) MarketColors.forChange(t.pnl) else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
