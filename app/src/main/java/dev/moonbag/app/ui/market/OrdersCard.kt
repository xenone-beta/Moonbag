@file:OptIn(ExperimentalMaterial3Api::class)

package dev.moonbag.app.ui.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.moonbag.app.R
import dev.moonbag.app.game.CoinDef
import dev.moonbag.app.game.Holding
import dev.moonbag.app.game.Order
import dev.moonbag.app.game.OrderError
import dev.moonbag.app.game.OrderFill
import dev.moonbag.app.game.OrderResult
import dev.moonbag.app.game.OrderType
import dev.moonbag.app.ui.components.CoinAvatar
import dev.moonbag.app.ui.formatAmount
import dev.moonbag.app.ui.formatPrice
import dev.moonbag.app.ui.formatTime
import dev.moonbag.app.ui.formatUsd
import java.math.BigDecimal
import java.math.MathContext

fun orderTypeName(type: OrderType): Int = when (type) {
    OrderType.LIMIT_BUY -> R.string.order_limit_buy
    OrderType.LIMIT_SELL -> R.string.order_limit_sell
    OrderType.STOP_LOSS -> R.string.order_stop
}

/** A price as plain digits with about six significant figures, for editing in a text field. */
private fun plain(value: Double): String =
    BigDecimal(value).round(MathContext(6)).stripTrailingZeros().toPlainString()

/** The orders on this coin, with a button to add one. */
@Composable
fun OrdersCard(
    coin: CoinDef,
    orders: List<Order>,
    fills: List<OrderFill>,
    onNew: () -> Unit,
    onCancel: (Long) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.orders),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (orders.isEmpty()) {
                Text(
                    stringResource(R.string.orders_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            orders.forEach { order -> OrderRow(order, coin, onCancel = { onCancel(order.id) }) }
            fills.take(2).forEach { f ->
                Text(
                    stringResource(R.string.order_filled, stringResource(orderTypeName(f.type)), formatPrice(f.price), formatTime(f.time)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.order_new))
            }
        }
    }
}

@Composable
fun OrderRow(order: Order, coin: CoinDef, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CoinAvatar(coin, size = 32.dp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                stringResource(orderTypeName(order.type)) + " · " + formatPrice(order.trigger),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (order.type == OrderType.LIMIT_BUY) formatUsd(order.amount) else "${formatAmount(order.amount)} ${coin.symbol}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.order_cancel))
        }
    }
}

@Composable
fun OrderSheet(
    coin: CoinDef,
    price: Double,
    cash: Double,
    holding: Holding?,
    onDismiss: () -> Unit,
    onPlace: (OrderType, Double, Double) -> OrderResult,
) {
    val liquid = holding?.liquid ?: 0.0
    var type by remember { mutableStateOf(OrderType.LIMIT_BUY) }
    var triggerText by remember { mutableStateOf(plain(price * 0.9)) }
    var fraction by remember { mutableFloatStateOf(0.5f) }
    var error by remember { mutableStateOf<OrderError?>(null) }

    val buying = type == OrderType.LIMIT_BUY
    val trigger = triggerText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val amount = (if (buying) cash else liquid) * if (fraction >= 0.999f) 1.0 else fraction.toDouble()
    val presets = if (type == OrderType.LIMIT_SELL) listOf(1.1, 1.25, 1.5) else listOf(0.95, 0.9, 0.8)

    fun choose(next: OrderType) {
        type = next
        error = null
        triggerText = plain(price * if (next == OrderType.LIMIT_SELL) 1.2 else if (next == OrderType.LIMIT_BUY) 0.9 else 0.9)
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
                Column {
                    Text(stringResource(R.string.order_new), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.order_now_price, formatPrice(price)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                OrderType.entries.forEachIndexed { index, t ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = { choose(t) },
                        shape = SegmentedButtonDefaults.itemShape(index, OrderType.entries.size),
                    ) { Text(stringResource(orderTypeName(t)), maxLines = 1) }
                }
            }

            OutlinedTextField(
                value = triggerText,
                onValueChange = { triggerText = it; error = null },
                label = { Text(stringResource(R.string.order_trigger)) },
                prefix = { Text("$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { f ->
                    val pct = Math.round((f - 1.0) * 100).toInt()
                    FilterChip(
                        selected = false,
                        onClick = { triggerText = plain(price * f); error = null },
                        label = { Text(if (pct > 0) "+$pct%" else "$pct%") },
                    )
                }
            }

            Text(
                if (buying) stringResource(R.string.order_amount_cash, formatUsd(amount))
                else stringResource(R.string.order_amount_coins, formatAmount(amount), coin.symbol),
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(value = fraction, onValueChange = { fraction = it; error = null }, valueRange = 0.01f..1f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.25f, 0.5f, 0.75f, 1f).forEach { f ->
                    FilterChip(
                        selected = fraction == f,
                        onClick = { fraction = f; error = null },
                        label = { Text("${(f * 100).toInt()}%") },
                    )
                }
            }

            if (trigger > 0 && amount > 0) {
                Text(
                    when (type) {
                        OrderType.LIMIT_BUY -> stringResource(R.string.order_summary_buy, formatUsd(amount), coin.symbol, formatPrice(trigger))
                        OrderType.LIMIT_SELL -> stringResource(R.string.order_summary_sell, formatAmount(amount), coin.symbol, formatPrice(trigger))
                        OrderType.STOP_LOSS -> stringResource(R.string.order_summary_stop, formatAmount(amount), coin.symbol, formatPrice(trigger))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let {
                Text(
                    stringResource(orderErrorText(it, type)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = {
                    when (val r = onPlace(type, trigger, amount)) {
                        is OrderResult.Placed -> onDismiss()
                        is OrderResult.Failed -> error = r.error
                    }
                },
                enabled = trigger > 0 && amount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.order_place)) }
        }
    }
}

private fun orderErrorText(error: OrderError, type: OrderType): Int = when (error) {
    OrderError.BAD_PRICE -> if (type == OrderType.LIMIT_SELL) R.string.order_err_above else R.string.order_err_below
    OrderError.NOT_ENOUGH_CASH -> R.string.order_err_cash
    OrderError.NOT_ENOUGH_COINS -> R.string.order_err_coins
    OrderError.TOO_MANY -> R.string.order_err_many
    OrderError.BAD_AMOUNT, OrderError.UNKNOWN_COIN -> R.string.order_err_amount
}
