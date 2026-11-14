@file:OptIn(ExperimentalMaterial3Api::class)

package dev.moonbag.app.ui.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.moonbag.app.R
import dev.moonbag.app.game.CoinDef
import dev.moonbag.app.game.Holding
import dev.moonbag.app.game.Listing
import dev.moonbag.app.game.Trade
import dev.moonbag.app.game.TradeError
import dev.moonbag.app.game.TradeResult
import dev.moonbag.app.game.TradeSide
import dev.moonbag.app.game.Trading
import dev.moonbag.app.ui.components.CoinAvatar
import dev.moonbag.app.ui.formatAmount
import dev.moonbag.app.ui.formatPercent
import dev.moonbag.app.ui.formatUsd
import java.util.Locale
import kotlin.math.floor

private val PERCENTS = listOf(25, 50, 75, 100)

private fun inputText(value: Double): String =
    String.format(Locale.US, "%.2f", floor(value * 100) / 100)

@Composable
fun TradeSheet(
    coin: CoinDef,
    price: Double,
    cash: Double,
    holding: Holding?,
    initialSide: TradeSide,
    onDismiss: () -> Unit,
    onBuy: (Double) -> TradeResult,
    onSell: (Double) -> TradeResult,
    onDone: (String) -> Unit,
) {
    val context = LocalContext.current
    var side by remember { mutableStateOf(if (initialSide == TradeSide.SELL && holding == null) TradeSide.BUY else initialSide) }
    var text by remember { mutableStateOf("") }
    var useAll by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<TradeError?>(null) }

    val buying = side == TradeSide.BUY
    val available = if (buying) cash else (holding?.liquid ?: 0.0) * price
    val typed = text.replace(',', '.').toDoubleOrNull() ?: 0.0
    val usd = if (useAll) available else typed
    val liquidity = coin.liquidity
    val netUsd = usd * (1 - Trading.FEE_RATE)
    val sellCoins = if (useAll || usd >= available * 0.9999) (holding?.liquid ?: 0.0) else usd / price
    val preview = if (buying) {
        Listing.coinsForUsd(price, liquidity, netUsd)
    } else {
        Listing.usdForCoins(price, liquidity, sellCoins) * (1 - Trading.FEE_RATE)
    }
    val impact = when {
        liquidity <= 0.0 || usd <= 0.0 -> 0.0
        buying -> Listing.priceAfterBuy(price, liquidity, netUsd) / price - 1.0
        else -> Listing.priceAfterSell(price, liquidity, sellCoins) / price - 1.0
    }
    val canConfirm = usd >= Trading.MIN_ORDER_USD

    fun confirm() {
        val result = if (buying) {
            onBuy(if (useAll) cash else usd)
        } else {
            val held = holding?.liquid ?: 0.0
            val allOfIt = useAll || usd >= available * 0.9999
            onSell(if (allOfIt) held else usd / price)
        }
        when (result) {
            is TradeResult.Ok -> {
                onDone(message(context, result.trade, coin))
                onDismiss()
            }
            is TradeResult.Failed -> error = result.error
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CoinAvatar(coin, size = 40.dp)
                Text(coin.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = buying,
                    onClick = { side = TradeSide.BUY; text = ""; useAll = false; error = null },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.buy)) }
                SegmentedButton(
                    selected = !buying,
                    onClick = { side = TradeSide.SELL; text = ""; useAll = false; error = null },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    enabled = holding != null && holding.liquid > 0.0,
                ) { Text(stringResource(R.string.sell)) }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input.filter { it.isDigit() || it == '.' || it == ',' }.take(14)
                    useAll = false
                    error = null
                },
                label = { Text(stringResource(R.string.amount_usd)) },
                prefix = { Text("$") },
                singleLine = true,
                isError = error != null,
                supportingText = {
                    Text(
                        error?.let { stringResource(errorText(it)) }
                            ?: stringResource(
                                if (buying) R.string.available_cash else R.string.available_position,
                                formatUsd(available),
                            ),
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PERCENTS.forEach { pct ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            text = inputText(available * pct / 100.0)
                            useAll = pct == 100
                            error = null
                        },
                        label = { Text("$pct%") },
                        enabled = available >= Trading.MIN_ORDER_USD,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SummaryLine(
                    label = stringResource(if (buying) R.string.you_get else R.string.you_receive),
                    value = if (buying) "≈ ${formatAmount(preview)} ${coin.symbol}" else "≈ ${formatUsd(preview)}",
                )
                if (liquidity > 0.0) {
                    SummaryLine(
                        label = stringResource(R.string.price_impact),
                        value = formatPercent(impact),
                    )
                }
                SummaryLine(
                    label = stringResource(R.string.fee_label, (Trading.FEE_RATE * 100).toString().trimEnd('0').trimEnd('.')),
                    value = formatUsd(usd * Trading.FEE_RATE),
                )
            }

            Spacer(Modifier.height(2.dp))
            Button(
                onClick = ::confirm,
                enabled = canConfirm,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    stringResource(if (buying) R.string.confirm_buy else R.string.confirm_sell, coin.symbol),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun errorText(error: TradeError): Int = when (error) {
    TradeError.NOT_ENOUGH_CASH -> R.string.err_not_enough_cash
    TradeError.NOT_ENOUGH_COINS -> R.string.err_not_enough_coins
    TradeError.TOO_SMALL -> R.string.err_too_small
    TradeError.UNKNOWN_COIN -> R.string.err_unknown
}

private fun message(context: android.content.Context, trade: Trade, coin: CoinDef): String =
    if (trade.side == TradeSide.BUY) {
        context.getString(R.string.trade_bought, formatAmount(trade.amount), coin.symbol, formatUsd(trade.usd))
    } else {
        context.getString(R.string.trade_sold, formatAmount(trade.amount), coin.symbol, formatUsd(trade.usd))
    }
