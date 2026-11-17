@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.moonbag.app.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.moonbag.app.R
import dev.moonbag.app.game.Chains
import dev.moonbag.app.game.Contact
import dev.moonbag.app.game.GameState
import dev.moonbag.app.game.Wallet
import dev.moonbag.app.game.WalletInfo
import dev.moonbag.app.ui.formatUsd

/** The four invented networks: fee, your address and how many of your coins live there. */
@Composable
fun NetworksScreen(state: GameState, wallet: WalletInfo, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Scaffold(topBar = { WalletTopBar(stringResource(R.string.networks_title), onBack) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(Chains.all, key = { it.id }) { chain ->
                val coins = state.allCoins.filter { Chains.of(it) == chain }
                val heldHere = coins.count { state.holdings.containsKey(it.id) }
                val address = wallet.addresses[chain.id].orEmpty()
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(chain.color)))
                            Text(chain.name, Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(chain.symbol, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            stringResource(R.string.network_stats, formatUsd(chain.feeUsd), coins.size, heldHere),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (address.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    Wallet.shorten(address),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { clipboard.setText(AnnotatedString(address)) }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Saved recipients. Rivals of the leaderboard are offered on the Send screen without being saved. */
@Composable
fun ContactsScreen(
    contacts: List<Contact>,
    onAdd: (name: String, address: String, chainId: String) -> Boolean,
    onRemove: (Contact) -> Unit,
    onBack: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { WalletTopBar(stringResource(R.string.contacts_title), onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, stringResource(R.string.contact_add)) }
        },
    ) { padding ->
        if (contacts.isEmpty()) {
            Text(
                stringResource(R.string.contacts_empty),
                Modifier.padding(padding).padding(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(contacts, key = { it.address }) { c ->
                    ListItem(
                        leadingContent = { Identicon(c.address, 40.dp) },
                        headlineContent = { Text(c.name) },
                        supportingContent = { Text(Wallet.shorten(c.address) + " · " + (Chains.get(c.chainId)?.name ?: "")) },
                        trailingContent = { IconButton(onClick = { onRemove(c) }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) } },
                    )
                }
            }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var chainId by remember { mutableStateOf(Chains.all.first().id) }
        var failed by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.contact_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.contact_name)) }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in Chains.all) FilterChip(selected = c.id == chainId, onClick = { chainId = c.id; failed = false }, label = { Text(c.name) })
                    }
                    OutlinedTextField(
                        address,
                        { address = it.trim(); failed = false },
                        label = { Text(stringResource(R.string.address)) },
                        isError = failed,
                        supportingText = { if (failed) Text(stringResource(R.string.send_err_address)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { if (onAdd(name, address, chainId)) adding = false else failed = true }, enabled = name.isNotBlank() && address.isNotBlank()) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
