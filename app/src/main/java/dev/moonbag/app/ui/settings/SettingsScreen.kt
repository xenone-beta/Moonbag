@file:OptIn(ExperimentalMaterial3Api::class)

package dev.moonbag.app.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.foundation.clickable
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.moonbag.app.BuildConfig
import dev.moonbag.app.R
import dev.moonbag.app.game.GameState
import dev.moonbag.app.ui.components.Stat
import dev.moonbag.app.ui.formatSignedUsd
import dev.moonbag.app.ui.formatUsd
import dev.moonbag.app.ui.theme.MarketColors

@Composable
fun SettingsScreen(
    state: GameState,
    dynamicColor: Boolean,
    haptics: Boolean,
    onDynamicColor: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onReset: () -> Unit,
    onOpenTutorial: () -> Unit,
    wallet: dev.moonbag.app.game.WalletInfo,
    onOpenSeed: () -> Unit,
    onOpenNetworks: () -> Unit,
    onOpenContacts: () -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_settings), fontWeight = FontWeight.Bold) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.about_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SwitchRow(R.string.set_dynamic, R.string.set_dynamic_desc, dynamicColor, onDynamicColor)
                    }
                    SwitchRow(R.string.set_haptics, R.string.set_haptics_desc, haptics, onHaptics)
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Stat(stringResource(R.string.fees_paid), formatUsd(state.feesPaid), Modifier.weight(1f))
                        Stat(
                            stringResource(R.string.realized),
                            formatSignedUsd(state.realizedPnl),
                            Modifier.weight(1f),
                            valueColor = MarketColors.forChange(state.realizedPnl),
                        )
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Stat(stringResource(R.string.trades_count), state.trades.size.toString(), Modifier.weight(1f))
                        Stat(stringResource(R.string.version), BuildConfig.VERSION_NAME, Modifier.weight(1f))
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.seed_screen_title)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    when {
                                        !wallet.exists -> R.string.wallet_status_none
                                        wallet.backedUp -> R.string.wallet_status_saved
                                        else -> R.string.wallet_status_unsaved
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.clickable(onClick = onOpenSeed),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.networks_title)) },
                        supportingContent = { Text(stringResource(R.string.networks_desc)) },
                        modifier = Modifier.clickable(onClick = onOpenNetworks),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.contacts_title)) },
                        supportingContent = { Text(stringResource(R.string.contacts_desc)) },
                        modifier = Modifier.clickable(onClick = onOpenContacts),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            OutlinedButton(
                onClick = onOpenTutorial,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.how_to_play)) }

            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.reset_game)) }

            Text(
                stringResource(R.string.offline_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_body)) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onReset() }) {
                    Text(stringResource(R.string.reset_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SwitchRow(title: Int, description: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
