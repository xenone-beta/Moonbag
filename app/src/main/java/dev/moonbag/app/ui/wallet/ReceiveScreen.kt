@file:OptIn(ExperimentalMaterial3Api::class)

package dev.moonbag.app.ui.wallet

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.moonbag.app.R
import dev.moonbag.app.game.Chains
import dev.moonbag.app.game.GameState
import dev.moonbag.app.game.Transfers
import dev.moonbag.app.game.WalletInfo
import dev.moonbag.app.game.WalletTx
import dev.moonbag.app.ui.formatAmount
import dev.moonbag.app.ui.formatDuration
import dev.moonbag.app.ui.formatUsd
import kotlinx.coroutines.delay

@Composable
fun ReceiveScreen(
    state: GameState,
    wallet: WalletInfo,
    onClaimAirdrop: () -> WalletTx?,
    onSetup: () -> Unit,
    onBack: () -> Unit,
) {
    var chainId by rememberSaveable { mutableStateOf(Chains.all.first().id) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var received by remember { mutableStateOf<WalletTx?>(null) }
    val chain = Chains.get(chainId) ?: Chains.all.first()
    val address = wallet.addresses[chain.id].orEmpty()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val wait = Transfers.airdropWait(state, now).let { if (state.lastAirdropAt <= 0) 0L else it }

    Scaffold(
        topBar = { WalletTopBar(stringResource(R.string.receive), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in Chains.all) {
                    FilterChip(selected = c.id == chain.id, onClick = { chainId = c.id }, label = { Text(c.name) })
                }
            }

            if (!wallet.exists) {
                Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.no_wallet_body))
                        Button(onClick = onSetup) { Text(stringResource(R.string.create_wallet)) }
                    }
                }
            } else {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Identicon(address, 96.dp)
                        Text(stringResource(R.string.address_on, chain.name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        SelectionContainer {
                            Text(
                                address,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                textAlign = TextAlign.Center,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(onClick = { clipboard.setText(AnnotatedString(address)) }) {
                                Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.copy), Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, address)
                                }
                                context.startActivity(Intent.createChooser(send, null))
                            }) {
                                Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.share), Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.receive_note, chain.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CardGiftcard, null, Modifier.size(32.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(R.string.airdrop_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (wait > 0) stringResource(R.string.airdrop_wait, formatDuration(wait / 1000)) else stringResource(R.string.airdrop_ready),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(onClick = { received = onClaimAirdrop() }, enabled = wait <= 0) { Text(stringResource(R.string.claim)) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    received?.let { tx ->
        val symbol = state.coin(tx.coinId)?.symbol ?: tx.coinId
        AlertDialog(
            onDismissRequest = { received = null },
            icon = { Icon(Icons.Filled.CardGiftcard, null) },
            title = { Text(stringResource(R.string.airdrop_got)) },
            text = { Text(stringResource(R.string.airdrop_got_body, formatAmount(tx.amount), symbol, formatUsd(tx.valueUsd))) },
            confirmButton = { TextButton(onClick = { received = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}
