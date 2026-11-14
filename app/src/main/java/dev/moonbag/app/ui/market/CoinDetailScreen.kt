@file:OptIn(ExperimentalMaterial3Api::class)

package dev.moonbag.app.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.moonbag.app.R
import dev.moonbag.app.game.CoinKind
import dev.moonbag.app.game.GameState
import dev.moonbag.app.game.Indicators
import dev.moonbag.app.game.TradeResult
import dev.moonbag.app.game.TradeSide
import dev.moonbag.app.ui.components.ChangeChip
import dev.moonbag.app.ui.components.CoinAvatar
import dev.moonbag.app.ui.components.FlashText
import dev.moonbag.app.ui.components.CandleChart
import dev.moonbag.app.ui.components.ChartOverlay
import dev.moonbag.app.ui.components.LineChart
import dev.moonbag.app.ui.components.RsiPanel
import dev.moonbag.app.ui.components.Stat
import dev.moonbag.app.ui.formatAmount
import dev.moonbag.app.ui.formatCompact
import dev.moonbag.app.ui.formatPercent
import dev.moonbag.app.ui.formatPrice
import dev.moonbag.app.ui.formatSignedUsd
import dev.moonbag.app.ui.formatTime
import dev.moonbag.app.ui.formatUsd
import dev.moonbag.app.ui.theme.MarketColors
import kotlinx.coroutines.launch

private data class Range(val points: Int, val label: Int, val perCandle: Int)

private val RANGES = listOf(
    Range(30, R.string.range_1m, 3),
    Range(150, R.string.range_5m, 6),
    Range(720, R.string.range_all, 24),
)

@Composable
fun CoinDetailScreen(
    coinId: String,
    state: GameState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onBuy: (Double) -> TradeResult,
    onSell: (Double) -> TradeResult,
    onCertificate: () -> Unit,
    onStake: (Double) -> Boolean,
    onUnstake: (Double) -> Boolean,
) {
    val coin = state.coin(coinId) ?: return
    val scope = rememberCoroutineScope()
    var rangeIndex by rememberSaveable { mutableIntStateOf(1) }
    var scrub by remember { mutableStateOf<Int?>(null) }
    var candleMode by rememberSaveable { mutableStateOf(false) }
    var showSma by rememberSaveable { mutableStateOf(false) }
    var showEma by rememberSaveable { mutableStateOf(false) }
    var showRsi by rememberSaveable { mutableStateOf(false) }
    var sheetSide by remember { mutableStateOf<TradeSide?>(null) }
    var stakeDialog by remember { mutableStateOf<Boolean?>(null) }

    val price = state.price(coinId)
    val holding = state.holdings[coinId]
    val range = RANGES[rangeIndex]
    val points = state.history[coinId].orEmpty().takeLast(range.points)
    val candles = Indicators.candles(points, range.perCandle)
    val series = if (candleMode) candles.map { it.close } else points
    val scrubbed = scrub?.let { series.getOrNull(it) }
    val shownPrice = scrubbed ?: price
    val change = if (series.isNotEmpty() && series.first() > 0) shownPrice / series.first() - 1.0 else 0.0
    val smaOverlay = if (showSma) ChartOverlay(Indicators.sma(series, (series.size / 4).coerceIn(3, 10)), MaterialTheme.colorScheme.tertiary) else null
    val emaOverlay = if (showEma) ChartOverlay(Indicators.ema(series, (series.size / 3).coerceIn(3, 20)), MaterialTheme.colorScheme.secondary) else null
    val overlays = listOfNotNull(smaOverlay, emaOverlay)
    val chartColor = MarketColors.forChange(if (points.size > 1) points.last() / points.first() - 1.0 else 0.0)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CoinAvatar(coin, size = 32.dp)
                        Column {
                            Text(coin.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                coin.symbol,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (coin.kind == CoinKind.CUSTOM) {
                        IconButton(onClick = onCertificate) {
                            Icon(
                                Icons.Filled.WorkspacePremium,
                                contentDescription = stringResource(R.string.view_certificate),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = { sheetSide = TradeSide.SELL },
                    enabled = holding != null && holding.liquid > 0.0,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(stringResource(R.string.sell), style = MaterialTheme.typography.titleMedium) }
                Button(
                    onClick = { sheetSide = TradeSide.BUY },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(stringResource(R.string.buy), style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (scrubbed != null) {
                    Text(
                        formatPrice(shownPrice),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    FlashText(
                        text = formatPrice(price),
                        value = price,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChangeChip(change)
                    Text(
                        stringResource(R.string.in_range, stringResource(range.label)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (candleMode) {
                val candle = scrub?.let { candles.getOrNull(it) } ?: candles.lastOrNull()
                if (candle != null) {
                    Text(
                        stringResource(
                            R.string.ohlc,
                            formatPrice(candle.open),
                            formatPrice(candle.high),
                            formatPrice(candle.low),
                            formatPrice(candle.close),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CandleChart(
                    candles = candles,
                    up = MarketColors.up(),
                    down = MarketColors.down(),
                    onScrub = { scrub = it },
                    overlays = overlays,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
            } else {
                LineChart(
                    points = points,
                    color = chartColor,
                    onScrub = { scrub = it },
                    overlays = overlays,
                    grid = true,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
            }

            if (showRsi) {
                val rsiSeries = Indicators.rsi(series, (series.size / 4).coerceIn(5, 14))
                val last = rsiSeries.lastOrNull { it != null }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.ind_rsi),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (last != null) {
                        Text(
                            stringResource(R.string.rsi_value, last.toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                RsiPanel(rsiSeries, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth().height(64.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RANGES.forEachIndexed { index, r ->
                    FilterChip(
                        selected = rangeIndex == index,
                        onClick = { rangeIndex = index; scrub = null },
                        label = { Text(stringResource(r.label)) },
                    )
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !candleMode,
                    onClick = { candleMode = false; scrub = null },
                    label = { Text(stringResource(R.string.chart_line)) },
                )
                FilterChip(
                    selected = candleMode,
                    onClick = { candleMode = true; scrub = null },
                    label = { Text(stringResource(R.string.chart_candles)) },
                )
                FilterChip(selected = showSma, onClick = { showSma = !showSma }, label = { Text(stringResource(R.string.ind_sma)) })
                FilterChip(selected = showEma, onClick = { showEma = !showEma }, label = { Text(stringResource(R.string.ind_ema)) })
                FilterChip(selected = showRsi, onClick = { showRsi = !showRsi }, label = { Text(stringResource(R.string.ind_rsi)) })
            }

            if (holding != null) {
                val value = holding.amount * price
                val pnl = value - holding.costBasis
                val pnlPct = if (holding.costBasis > 0) pnl / holding.costBasis else 0.0
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.your_position),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Stat(stringResource(R.string.value), formatUsd(value), Modifier.weight(1f))
                            Stat(
                                stringResource(R.string.holding),
                                "${formatAmount(holding.amount)} ${coin.symbol}",
                                Modifier.weight(1f),
                            )
                        }
                        Row(Modifier.fillMaxWidth()) {
                            Stat(stringResource(R.string.avg_buy), formatPrice(holding.averagePrice), Modifier.weight(1f))
                            Stat(
                                stringResource(R.string.profit),
                                "${formatSignedUsd(pnl)} (${formatPercent(pnlPct)})",
                                Modifier.weight(1f),
                                valueColor = MarketColors.forChange(pnl),
                            )
                        }
                    }
                }
            }

            if (holding != null) {
                StakingCard(
                    coin = coin,
                    holding = holding,
                    onStake = { stakeDialog = true },
                    onUnstake = { stakeDialog = false },
                )
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Stat(stringResource(R.string.market_cap), "$" + formatCompact(price * coin.supply), Modifier.weight(1f))
                        Stat(stringResource(R.string.supply), formatAmount(coin.supply), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Stat(stringResource(R.string.risk), stringResource(riskLabel(coin.risk)), Modifier.weight(1f))
                        Row(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            repeat(4) { i ->
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (i < coin.risk) riskColor(coin.risk)
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                        ),
                                )
                            }
                        }
                    }
                }
            }

            if (coin.kind == CoinKind.CUSTOM) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.thin_market_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(stringResource(R.string.thin_market_note), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            val trades = state.trades.filter { it.coinId == coinId }.take(5)
            if (trades.isNotEmpty()) {
                Text(
                    stringResource(R.string.your_trades),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                trades.forEach { t ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(
                                    if (t.side == TradeSide.BUY) R.string.trade_line_buy else R.string.trade_line_sell,
                                    formatAmount(t.amount),
                                    coin.symbol,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${formatTime(t.time)} · ${formatPrice(t.price)}",
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
            Spacer(Modifier.height(8.dp))
        }
    }

    stakeDialog?.let { staking ->
        val h = holding
        if (h != null) {
            StakeDialog(
                coin = coin,
                staking = staking,
                maxAmount = if (staking) h.liquid else h.staked,
                onDismiss = { stakeDialog = null },
                onConfirm = { amount ->
                    val ok = if (staking) onStake(amount) else onUnstake(amount)
                    if (ok) stakeDialog = null
                },
            )
        } else {
            stakeDialog = null
        }
    }

    sheetSide?.let { side ->
        TradeSheet(
            coin = coin,
            price = price,
            cash = state.cash,
            holding = holding,
            initialSide = side,
            onDismiss = { sheetSide = null },
            onBuy = onBuy,
            onSell = onSell,
            onDone = { message -> scope.launch { snackbar.showSnackbar(message) } },
        )
    }
}

private fun riskLabel(risk: Int): Int = when (risk) {
    1 -> R.string.risk_low
    2 -> R.string.risk_medium
    3 -> R.string.risk_high
    else -> R.string.risk_extreme
}

@Composable
private fun riskColor(risk: Int) = when (risk) {
    1 -> MarketColors.up()
    2 -> MaterialTheme.colorScheme.tertiary
    else -> MarketColors.down()
}
