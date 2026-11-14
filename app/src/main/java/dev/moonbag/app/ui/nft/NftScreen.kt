@file:OptIn(ExperimentalMaterial3Api::class)

package dev.moonbag.app.ui.nft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import dev.moonbag.app.R
import dev.moonbag.app.game.GameState
import dev.moonbag.app.game.MintResult
import dev.moonbag.app.game.Nft
import dev.moonbag.app.game.NftCollection
import dev.moonbag.app.game.Nfts
import dev.moonbag.app.game.Rarity
import dev.moonbag.app.ui.components.ChangeChip
import dev.moonbag.app.ui.components.Sparkline
import dev.moonbag.app.ui.formatPrice
import dev.moonbag.app.ui.formatUsd
import dev.moonbag.app.ui.theme.MarketColors

private fun collectionName(id: String): Int = when (id) {
    "pixel" -> R.string.nft_pixel
    "orbs" -> R.string.nft_orbs
    else -> R.string.nft_rocks
}

private fun rarityName(r: Rarity): Int = when (r) {
    Rarity.COMMON -> R.string.rarity_common
    Rarity.RARE -> R.string.rarity_rare
    Rarity.EPIC -> R.string.rarity_epic
    Rarity.LEGENDARY -> R.string.rarity_legendary
}

@Composable
fun NftScreen(
    state: GameState,
    onMint: (String) -> MintResult,
    onSell: (Long) -> Double?,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var revealed by remember { mutableStateOf<Nft?>(null) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_nft), fontWeight = FontWeight.Bold) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    stringResource(R.string.nft_intro, (Nfts.SALE_FEE * 100).toString(), Nfts.MAX_OWNED),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Nfts.collections.forEach { col ->
                item(key = "col-${col.id}", span = { GridItemSpan(2) }) {
                    CollectionCard(col, state, onMint = {
                        when (val r = onMint(col.id)) {
                            is MintResult.Minted -> revealed = r.nft
                            MintResult.NotEnoughCash -> message = R.string.nft_no_cash
                            MintResult.LimitReached -> message = R.string.nft_limit
                        }
                    })
                }
            }
            item(span = { GridItemSpan(2) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.nft_owned, state.nfts.size, Nfts.MAX_OWNED),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(formatUsd(state.nftValue()), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (state.nfts.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        stringResource(R.string.nft_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.nfts, key = { it.id }) { nft ->
                Card(
                    onClick = { selected = nft.id },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        NftArt(nft, Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.nft_item_title, stringResource(collectionName(nft.collection)), nft.id),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            stringResource(rarityName(nft.rarity)) + " · " + formatUsd(Nfts.value(state, nft)),
                            style = MaterialTheme.typography.bodySmall,
                            color = rarityColor(nft.rarity),
                        )
                    }
                }
            }
        }
    }

    revealed?.let { nft ->
        AlertDialog(
            onDismissRequest = { revealed = null },
            title = { Text(stringResource(rarityName(nft.rarity))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NftArt(nft, Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.nft_reveal, formatUsd(nft.paid), formatUsd(Nfts.value(state, nft))),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { revealed = null }) { Text(stringResource(R.string.ok)) } },
        )
    }

    selected?.let { id ->
        val nft = state.nfts.firstOrNull { it.id == id }
        if (nft == null) {
            selected = null
        } else {
            val gross = Nfts.value(state, nft)
            AlertDialog(
                onDismissRequest = { selected = null },
                title = { Text(stringResource(R.string.nft_item_title, stringResource(collectionName(nft.collection)), nft.id)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        NftArt(nft, Modifier.fillMaxWidth())
                        Text(
                            stringResource(
                                R.string.nft_sell_info,
                                stringResource(rarityName(nft.rarity)),
                                formatUsd(nft.paid),
                                formatUsd(gross * (1 - Nfts.SALE_FEE)),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { onSell(nft.id); selected = null }) { Text(stringResource(R.string.nft_sell)) }
                },
                dismissButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }

    message?.let { res ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(stringResource(res)) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun CollectionCard(col: NftCollection, state: GameState, onMint: () -> Unit) {
    val floor = Nfts.floor(state, col.id)
    val change = Nfts.floorChange(state, col.id)
    val history = state.nftHistory[col.id].orEmpty()
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(collectionName(col.id)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(R.string.nft_floor, formatPrice(floor)), style = MaterialTheme.typography.bodyMedium)
                ChangeChip(change)
            }
            if (history.size > 1) {
                Sparkline(history, MarketColors.forChange(change), Modifier.size(width = 84.dp, height = 40.dp))
            }
            Column(Modifier.padding(start = 12.dp)) {
                Button(onClick = onMint, enabled = state.cash + 1e-9 >= col.mintPrice) {
                    Text(stringResource(R.string.nft_mint, formatUsd(col.mintPrice)))
                }
            }
        }
    }
}
