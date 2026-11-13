@file:OptIn(ExperimentalMaterial3Api::class)

package dev.moonbag.app.ui.market

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.moonbag.app.R
import dev.moonbag.app.game.CoinDef
import dev.moonbag.app.game.CoinKind
import dev.moonbag.app.game.EventKind
import dev.moonbag.app.game.GameState
import dev.moonbag.app.game.Listing
import dev.moonbag.app.game.MarketEvent
import dev.moonbag.app.ui.components.ChangeChip
import dev.moonbag.app.ui.components.CoinAvatar
import dev.moonbag.app.ui.components.FlashText
import dev.moonbag.app.ui.components.Sparkline
import dev.moonbag.app.ui.formatPercent
import dev.moonbag.app.ui.formatPrice
import dev.moonbag.app.ui.formatSignedUsd
import dev.moonbag.app.ui.formatUsd
import dev.moonbag.app.ui.theme.MarketColors

private enum class Filter(val label: Int) {
    ALL(R.string.filter_all),
    MAJORS(R.string.filter_majors),
    MEMES(R.string.filter_memes),
    CREATED(R.string.filter_created),
    GAINERS(R.string.filter_gainers),
    LOSERS(R.string.filter_losers),
    HELD(R.string.filter_held),
}

@Composable
fun MarketScreen(
    state: GameState,
    onOpenCoin: (String) -> Unit,
    onGoMine: () -> Unit,
    onCreateCoin: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val everything = state.allCoins
    fun cap(c: CoinDef) = c.supply * state.price(c.id)
    val coins = when (filter) {
        Filter.ALL -> everything.sortedByDescending { cap(it) }
        Filter.MAJORS -> everything.filter { it.kind == CoinKind.MAJOR }.sortedByDescending { cap(it) }
        Filter.MEMES -> everything.filter { it.kind == CoinKind.MEME }.sortedByDescending { cap(it) }
        Filter.CREATED -> everything.filter { it.kind == CoinKind.CUSTOM }.sortedByDescending { it.serial }
        Filter.GAINERS -> everything.sortedByDescending { state.change(it.id) }
        Filter.LOSERS -> everything.sortedBy { state.change(it.id) }
        Filter.HELD -> everything.filter { it.id in state.holdings }
            .sortedByDescending { state.holdings.getValue(it.id).amount * state.price(it.id) }
    }
    val broke = state.cash < 1.0 && state.holdings.isEmpty()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_market), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onCreateCoin) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.create_coin))
                    }
                },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary") { SummaryCard(state) }

            if (broke) {
                item(key = "broke") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.broke_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(stringResource(R.string.broke_body), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onGoMine) { Text(stringResource(R.string.go_mine)) }
                        }
                    }
                }
            }

            if (state.customCoins.isEmpty()) {
                item(key = "promo") {
                    Card(
                        onClick = onCreateCoin,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.promo_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.promo_body, formatUsd(Listing.FEE)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            state.events.firstOrNull()?.let { event ->
                item(key = "news") { NewsCard(state, event, onClick = { onOpenCoin(event.coinId) }) }
            }

            item(key = "filters") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Filter.entries.forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(stringResource(f.label)) },
                        )
                    }
                }
            }

            if (coins.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.nothing_here),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            items(coins, key = { it.id }) { coin ->
                CoinRow(
                    state = state,
                    coinId = coin.id,
                    onClick = { onOpenCoin(coin.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(state: GameState) {
    val profit = state.totalProfit()
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.net_worth),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
            FlashText(
                text = formatUsd(state.netWorth()),
                value = state.netWorth(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                baseColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = formatSignedUsd(profit),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MarketColors.forChange(profit),
                )
                Text(
                    text = stringResource(R.string.cash_available, formatUsd(state.cash)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun NewsCard(state: GameState, event: MarketEvent, onClick: () -> Unit) {
    val coin = state.coin(event.coinId) ?: return
    val pct = formatPercent(event.percent)
    val text = when (event.kind) {
        EventKind.PUMP -> stringResource(R.string.news_pump, coin.emoji, coin.symbol, pct)
        EventKind.DUMP -> stringResource(R.string.news_dump, coin.emoji, coin.symbol, pct)
        EventKind.RUG -> stringResource(R.string.news_rug, coin.symbol, pct)
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.news_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CoinRow(state: GameState, coinId: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val coin = state.coin(coinId) ?: return
    val price = state.price(coinId)
    val change = state.change(coinId)
    val held = state.holdings[coinId]

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoinAvatar(coin)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(coin.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (held != null) {
                        stringResource(R.string.row_held, coin.symbol, formatUsd(held.amount * price))
                    } else {
                        coin.symbol
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Sparkline(
                points = state.history[coinId].orEmpty().takeLast(60),
                color = MarketColors.forChange(change),
                modifier = Modifier.width(56.dp).height(28.dp),
            )
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
                FlashText(
                    text = formatPrice(price),
                    value = price,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                ChangeChip(change)
            }
        }
    }
}
