@file:OptIn(ExperimentalMaterial3Api::class)

package dev.moonbag.app.ui.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.moonbag.app.R
import dev.moonbag.app.game.CoinDef
import dev.moonbag.app.game.GameState
import dev.moonbag.app.game.SwapResult
import dev.moonbag.app.game.Swaps
import dev.moonbag.app.game.TradeError
import dev.moonbag.app.ui.components.CoinAvatar
import dev.moonbag.app.ui.formatAmount
import dev.moonbag.app.ui.formatPercent
import dev.moonbag.app.ui.formatUsd
import kotlinx.coroutines.launch

@Composable
fun SwapScreen(
    state: GameState,
    onSwap: (from: String, to: String, amount: Double) -> SwapResult,
    onEarn: () -> Unit,
) {
    val owned = state.holdings.keys.mapNotNull { state.coin(it) }.filter { (state.holdings[it.id]?.liquid ?: 0.0) > 0.0 }
    var fromId by rememberSaveable { mutableStateOf(owned.firstOrNull()?.id ?: "btc") }
    var toId by rememberSaveable { mutableStateOf(if (fromId == "eth") "btc" else "eth") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<SwapResult.Ok?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val from = state.coin(fromId)
    val to = state.coin(toId)
    val balance = state.holdings[fromId]?.liquid ?: 0.0
    val amount = parseAmount(amountText)
    val enough = amount != null && amount <= balance * (1 + 1e-9)
    val quote = if (from != null && to != null && amount != null && enough && fromId != toId) Swaps.quote(state, fromId, toId, amount) else null
    val failed = stringResource(R.string.swap_failed)
    val tooSmall = stringResource(R.string.swap_too_small)

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.swap), fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwapCard(
                label = stringResource(R.string.swap_from),
                coin = from,
                onPick = { picking = "from" },
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    placeholder = { Text("0") },
                    isError = amountText.isNotEmpty() && (amount == null || !enough),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.End),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.balance_of, formatAmount(balance), from?.symbol.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                TextButton(onClick = { amountText = trimAmount(balance) }, enabled = balance > 0) { Text(stringResource(R.string.max)) }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FilledTonalIconButton(onClick = {
                    val f = fromId
                    fromId = toId
                    toId = f
                    amountText = ""
                }) { Icon(Icons.Filled.SwapVert, stringResource(R.string.swap_flip)) }
            }

            SwapCard(
                label = stringResource(R.string.swap_to),
                coin = to,
                onPick = { picking = "to" },
            ) {
                Text(
                    quote?.let { formatAmount(it.toAmount) } ?: "0",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.End,
                    color = if (quote == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
            }

            if (from != null && to != null && fromId != toId) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val rate = state.price(fromId) / state.price(toId)
                        Detail(stringResource(R.string.swap_rate), "1 ${from.symbol} ≈ ${formatAmount(rate)} ${to.symbol}")
                        Detail(stringResource(R.string.swap_cost), if (quote != null) formatPercent(-quote.loss).removePrefix("+") else "—")
                        Detail(stringResource(R.string.swap_fees), if (quote != null) formatUsd(quote.feeUsd) else "—")
                        Detail(stringResource(R.string.network_fee), stringResource(R.string.swap_no_network_fee))
                    }
                }
            }

            if (owned.isEmpty()) {
                Text(stringResource(R.string.swap_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onEarn, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.go_mine)) }
            }

            Button(
                onClick = {
                    val a = amount ?: return@Button
                    when (val r = onSwap(fromId, toId, a)) {
                        is SwapResult.Ok -> { done = r; amountText = "" }
                        is SwapResult.Failed -> scope.launch { snackbar.showSnackbar(if (r.trade == TradeError.TOO_SMALL) tooSmall else failed) }
                    }
                },
                enabled = quote != null,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 0.dp),
            ) { Text(stringResource(R.string.swap), fontSize = 16.sp) }
            Spacer(Modifier.height(16.dp))
        }
    }

    picking?.let { side ->
        val list: List<CoinDef> = if (side == "from") owned else state.allCoins
        CoinPickerDialog(
            title = stringResource(if (side == "from") R.string.swap_from else R.string.swap_to),
            state = state,
            coins = list,
            onPick = { c ->
                if (side == "from") {
                    fromId = c.id
                    if (toId == c.id) toId = fromIdFallback(c.id)
                } else {
                    toId = c.id
                    if (fromId == c.id) fromId = fromIdFallback(c.id)
                }
                amountText = ""
                picking = null
            },
            onDismiss = { picking = null },
        )
    }

    done?.let { r ->
        val a = state.coin(r.tx.coinId)?.symbol.orEmpty()
        val b = state.coin(r.tx.toCoinId)?.symbol.orEmpty()
        AlertDialog(
            onDismissRequest = { done = null },
            icon = { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.swap_done)) },
            text = { Text(stringResource(R.string.swap_done_body, formatAmount(r.tx.amount), a, formatAmount(r.tx.toAmount), b)) },
            confirmButton = { TextButton(onClick = { done = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

private fun fromIdFallback(taken: String): String = if (taken == "btc") "eth" else "btc"

@Composable
private fun SwapCard(label: String, coin: CoinDef?, onPick: () -> Unit, field: @Composable RowScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onPick, shape = CircleShape, contentPadding = PaddingValues(start = 6.dp, end = 12.dp)) {
                    if (coin != null) {
                        CoinAvatar(coin, size = 28.dp)
                        Text(coin.symbol, Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp))
                }
                field()
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}
