@file:OptIn(ExperimentalMaterial3Api::class)

package dev.moonbag.app.ui.wallet

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.moonbag.app.R

/** An invented "site" of the in-game browser. Each one is a place inside the game. */
data class Dapp(
    val id: String,
    val url: String,
    @StringRes val name: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
    val color: Long,
)

val dapps: List<Dapp> = listOf(
    Dapp("swap", "moon://swap", R.string.dapp_swap, R.string.dapp_swap_desc, Icons.Filled.SwapHoriz, 0xFF7C4DFF),
    Dapp("mint", "moon://mint", R.string.dapp_mint, R.string.dapp_mint_desc, Icons.Filled.Diamond, 0xFFAB47BC),
    Dapp("mine", "moon://mine", R.string.dapp_mine, R.string.dapp_mine_desc, Icons.Filled.Bolt, 0xFFFFA726),
    Dapp("launch", "moon://launch", R.string.dapp_launch, R.string.dapp_launch_desc, Icons.Filled.RocketLaunch, 0xFFEF5350),
    Dapp("airdrop", "moon://airdrop", R.string.dapp_airdrop, R.string.dapp_airdrop_desc, Icons.Filled.CardGiftcard, 0xFF26A69A),
    Dapp("board", "moon://board", R.string.dapp_board, R.string.dapp_board_desc, Icons.Filled.EmojiEvents, 0xFFFFCA28),
    Dapp("stats", "moon://stats", R.string.dapp_stats, R.string.dapp_stats_desc, Icons.Filled.ShowChart, 0xFF42A5F5),
    Dapp("networks", "moon://networks", R.string.dapp_networks, R.string.dapp_networks_desc, Icons.Filled.Hub, 0xFF66BB6A),
)

/** The dapp an address bar entry points to, or null. "swap", "moon://swap" and "MOON://SWAP/" all match. */
fun findDapp(input: String): Dapp? {
    val t = input.trim().lowercase().removeSuffix("/")
    if (t.isEmpty()) return null
    return dapps.firstOrNull { it.url == t || it.id == t || it.url.removePrefix("moon://") == t }
}

@Composable
fun BrowserScreen(onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var notFound by remember { mutableStateOf(false) }
    val names = dapps.associate { it.id to stringResource(it.name) }
    val shown = if (query.isBlank() || query.startsWith("moon://")) dapps else dapps.filter {
        names.getValue(it.id).contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.tab_browser), fontWeight = FontWeight.Bold) }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; notFound = false },
                placeholder = { Text(stringResource(R.string.browser_hint)) },
                leadingIcon = { Icon(Icons.Filled.Lock, null, Modifier.size(18.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = ""; notFound = false }) { Icon(Icons.Filled.Close, null) }
                },
                shape = CircleShape,
                singleLine = true,
                isError = notFound,
                supportingText = { if (notFound) Text(stringResource(R.string.browser_not_found)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = {
                    val hit = findDapp(query)
                    if (hit != null) onOpen(hit.id) else notFound = query.isNotBlank()
                }),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.browser_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(shown, key = { it.id }) { d ->
                    val tint = Color(d.color)
                    Card(
                        onClick = { onOpen(d.id) },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(d.icon, null, tint = tint) }
                            Text(stringResource(d.name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(d.description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2)
                            Text(d.url, style = MaterialTheme.typography.labelSmall, color = tint)
                        }
                    }
                }
            }
        }
    }
}
