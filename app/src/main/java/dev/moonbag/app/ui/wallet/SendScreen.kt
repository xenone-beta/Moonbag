@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.moonbag.app.ui.wallet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.moonbag.app.R
import dev.moonbag.app.game.Chains
import dev.moonbag.app.game.Contact
import dev.moonbag.app.game.GameState
import dev.moonbag.app.game.Rivals
import dev.moonbag.app.game.SendError
import dev.moonbag.app.game.SendResult
import dev.moonbag.app.game.Wallet
import dev.moonbag.app.game.WalletInfo
import dev.moonbag.app.ui.components.CoinAvatar
import dev.moonbag.app.ui.formatAmount
import dev.moonbag.app.ui.formatUsd
import kotlinx.coroutines.launch

@Composable
fun SendScreen(
    state: GameState,
    wallet: WalletInfo,
    contacts: List<Contact>,
    startCoinId: String?,
    onSend: (coinId: String, amount: Double, to: String) -> SendResult,
    onBack: () -> Unit,
) {
    val sendable = state.holdings.keys.mapNotNull { state.coin(it) }.filter { (state.holdings[it.id]?.liquid ?: 0.0) > 0.0 }
    var coinId by rememberSaveable { mutableStateOf(startCoinId?.takeIf { id -> sendable.any { it.id == id } } ?: sendable.firstOrNull()?.id) }
    var address by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val coin = coinId?.let { state.coin(it) }
    val chain = coin?.let { Chains.of(it) }
    val liquid = coin?.let { state.holdings[it.id]?.liquid } ?: 0.0
    val amount = parseAmount(amountText)
    val addressOk = chain != null && Wallet.isAddress(chain, address)
    val own = Wallet.canonical(address) in wallet.ownAddresses
    val enough = amount != null && amount <= liquid * (1 + 1e-9)
    val feeOk = chain != null && state.cash + 1e-9 >= chain.feeUsd
    val ready = coin != null && chain != null && amount != null && enough && addressOk && !own && feeOk

    val messages = mapOf(
        SendError.UNKNOWN_COIN to stringResource(R.string.send_err_generic),
        SendError.BAD_AMOUNT to stringResource(R.string.send_err_amount),
        SendError.NOT_ENOUGH_COINS to stringResource(R.string.send_err_coins),
        SendError.BAD_ADDRESS to stringResource(R.string.send_err_address),
        SendError.OWN_ADDRESS to stringResource(R.string.send_err_own),
        SendError.NOT_ENOUGH_FEE to stringResource(R.string.send_err_fee),
    )

    Scaffold(
        topBar = { WalletTopBar(stringResource(R.string.send), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                onClick = { picking = true },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (coin != null && chain != null) {
                        CoinAvatar(coin)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(coin.name, style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.balance_of, formatAmount(liquid), coin.symbol), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ChainBadge(chain)
                            }
                        }
                    } else {
                        Text(stringResource(R.string.pick_token), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    }
                    Icon(Icons.Filled.KeyboardArrowDown, null)
                }
            }

            OutlinedTextField(
                value = address,
                onValueChange = { address = it.trim() },
                label = { Text(if (chain != null) stringResource(R.string.address_on, chain.name) else stringResource(R.string.address)) },
                isError = address.isNotEmpty() && (!addressOk || own),
                supportingText = {
                    when {
                        address.isNotEmpty() && own -> Text(stringResource(R.string.send_err_own))
                        address.isNotEmpty() && !addressOk -> Text(stringResource(R.string.send_err_address))
                    }
                },
                trailingIcon = {
                    IconButton(onClick = { clipboard.getText()?.text?.let { address = it.trim() } }) {
                        Icon(Icons.Filled.ContentPaste, stringResource(R.string.paste))
                    }
                },
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (chain != null) {
                val saved = contacts.filter { it.chainId == chain.id }
                val traders = Rivals.all.take(4)
                if (saved.isNotEmpty() || traders.isNotEmpty()) {
                    Text(stringResource(R.string.recipients), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (c in saved) {
                            AssistChip(onClick = { address = c.address }, label = { Text(c.name) }, leadingIcon = { Icon(Icons.Filled.Person, null, Modifier.size(18.dp)) })
                        }
                        for (r in traders) {
                            AssistChip(onClick = { address = Wallet.rivalAddress(r.id, chain) }, label = { Text(r.name) })
                        }
                    }
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text(stringResource(R.string.amount)) },
                isError = amountText.isNotEmpty() && (amount == null || !enough),
                supportingText = {
                    if (amount != null && coin != null) Text("≈ " + formatUsd(amount * state.price(coin.id)))
                },
                trailingIcon = {
                    TextButton(onClick = { amountText = trimAmount(liquid) }, enabled = liquid > 0) { Text(stringResource(R.string.max)) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (chain != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.network_fee), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatUsd(chain.feeUsd) + " · " + chain.name, fontWeight = FontWeight.SemiBold)
                }
                if (!feeOk) Text(stringResource(R.string.send_err_fee), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Text(stringResource(R.string.send_game_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Button(
                onClick = { reviewing = true },
                enabled = ready,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.review)) }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (reviewing && coin != null && chain != null && amount != null) {
        AlertDialog(
            onDismissRequest = { reviewing = false },
            title = { Text(stringResource(R.string.confirm_send)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewLine(stringResource(R.string.review_amount), formatAmount(amount) + " " + coin.symbol)
                    ReviewLine(stringResource(R.string.review_value), formatUsd(amount * state.price(coin.id)))
                    ReviewLine(stringResource(R.string.review_to), Wallet.shorten(address))
                    ReviewLine(stringResource(R.string.review_network), chain.name)
                    ReviewLine(stringResource(R.string.network_fee), formatUsd(chain.feeUsd))
                    Text(stringResource(R.string.send_final), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    reviewing = false
                    when (val r = onSend(coin.id, amount, address)) {
                        is SendResult.Ok -> done = coin.symbol
                        is SendResult.Failed -> scope.launch { snackbar.showSnackbar(messages.getValue(r.error)) }
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { reviewing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    done?.let { symbol ->
        AlertDialog(
            onDismissRequest = { done = null; onBack() },
            icon = { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.send_done)) },
            text = { Text(stringResource(R.string.send_done_body, symbol)) },
            confirmButton = { TextButton(onClick = { done = null; onBack() }) { Text(stringResource(R.string.ok)) } },
        )
    }

    if (picking) {
        CoinPickerDialog(
            title = stringResource(R.string.pick_token),
            state = state,
            coins = sendable,
            onPick = { coinId = it.id; amountText = ""; picking = false },
            onDismiss = { picking = false },
        )
    }
}

/** A balance as text for the amount field, without a long tail of digits. */
fun trimAmount(value: Double): String {
    if (value <= 0.0) return ""
    val floored = kotlin.math.floor(value * 1e8) / 1e8
    return java.math.BigDecimal(floored).setScale(8, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString()
}

@Composable
private fun ReviewLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
