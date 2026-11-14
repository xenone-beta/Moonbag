@file:OptIn(ExperimentalMaterial3Api::class)

package dev.moonbag.app.ui.market

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import dev.moonbag.app.CoinImages
import dev.moonbag.app.game.CustomCoin
import dev.moonbag.app.ui.components.CoinAvatar
import dev.moonbag.app.ui.components.CoinIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.moonbag.app.R
import dev.moonbag.app.game.CoinStyle
import dev.moonbag.app.game.GameState
import dev.moonbag.app.game.Listing
import dev.moonbag.app.game.ListingError
import dev.moonbag.app.game.ListingResult
import dev.moonbag.app.ui.formatPrice
import dev.moonbag.app.ui.formatUsd

private val COLORS = listOf(
    0xFF7C4DFF, 0xFF00BFA5, 0xFFFF6D00, 0xFFE91E63,
    0xFF2979FF, 0xFFFFC400, 0xFF43A047, 0xFFEF5350,
)

@Composable
fun CreateCoinScreen(
    state: GameState,
    onBack: () -> Unit,
    onCreate: (name: String, ticker: String, icon: String, color: Long, style: CoinStyle, imageFile: String?) -> ListingResult,
    onListed: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var ticker by rememberSaveable { mutableStateOf("") }
    var iconIndex by rememberSaveable { mutableIntStateOf(0) }
    var imageFile by rememberSaveable { mutableStateOf<String?>(null) }
    var listed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = withContext(Dispatchers.IO) { CoinImages.importPhoto(context, uri) }
                if (name != null) {
                    imageFile?.let { CoinImages.delete(context, it) }
                    imageFile = name
                }
            }
        }
    }
    // A photo that was picked but never used for a listing is not kept.
    DisposableEffect(Unit) {
        onDispose { if (!listed) imageFile?.let { CoinImages.delete(context, it) } }
    }
    var colorIndex by rememberSaveable { mutableIntStateOf(0) }
    var style by rememberSaveable { mutableStateOf(CoinStyle.MEME) }
    var error by rememberSaveable { mutableStateOf<ListingError?>(null) }

    val symbol = Listing.cleanTicker(ticker)
    val nameOk = name.trim().length in Listing.MIN_NAME..Listing.MAX_NAME
    val tickerTaken = state.allCoins.any { it.symbol == symbol }
    val tickerOk = symbol.length >= Listing.MIN_TICKER && symbol.any { it in 'A'..'Z' } && !tickerTaken
    val enoughCash = state.cash + 1e-9 >= Listing.FEE
    val limitReached = state.customCoins.size >= Listing.MAX_COINS
    val canLaunch = nameOk && tickerOk && enoughCash && !limitReached
    val color = COLORS[colorIndex]
    val preview = CustomCoin(
        id = "preview",
        symbol = symbol,
        name = name,
        icon = CoinIcons.choices[iconIndex],
        color = color,
        style = style,
        serial = 0,
        createdAt = 0L,
        imageFile = imageFile,
    ).toDef()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!enoughCash) {
                    Text(
                        stringResource(R.string.need_more, formatUsd(Listing.FEE - state.cash)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = {
                        when (val result = onCreate(name, ticker, CoinIcons.choices[iconIndex], color, style, imageFile)) {
                            is ListingResult.Listed -> {
                                listed = true
                                onListed(result.coin.id)
                            }
                            is ListingResult.Failed -> error = result.error
                        }
                    },
                    enabled = canLaunch,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(
                        stringResource(R.string.launch_button, formatUsd(Listing.FEE)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
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
            // Live preview
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoinAvatar(preview, size = 56.dp)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(
                            name.trim().ifEmpty { stringResource(R.string.preview_name) },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            symbol.ifEmpty { "TICK" } + " · " + stringResource(R.string.start_price, formatPrice(style.startPrice)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(Listing.MAX_NAME); error = null },
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                isError = error == ListingError.BAD_NAME,
                supportingText = if (error == ListingError.BAD_NAME) {
                    { Text(stringResource(R.string.err_bad_name, Listing.MIN_NAME, Listing.MAX_NAME)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ticker,
                onValueChange = { ticker = Listing.cleanTicker(it); error = null },
                label = { Text(stringResource(R.string.field_ticker)) },
                singleLine = true,
                isError = tickerTaken || error == ListingError.BAD_TICKER || error == ListingError.TICKER_TAKEN,
                supportingText = when {
                    tickerTaken -> {{ Text(stringResource(R.string.err_ticker_taken)) }}
                    error == ListingError.BAD_TICKER -> {
                        { Text(stringResource(R.string.err_bad_ticker, Listing.MIN_TICKER, Listing.MAX_TICKER)) }
                    }
                    else -> null
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.field_icon), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (imageFile != null) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                        .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.pick_photo),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CoinIcons.choices.forEachIndexed { i, key ->
                    val selected = i == iconIndex && imageFile == null
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            .clickable {
                                iconIndex = i
                                imageFile?.let { CoinImages.delete(context, it) }
                                imageFile = null
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(CoinIcons.get(key), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Text(
                stringResource(if (imageFile != null) R.string.photo_selected else R.string.photo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(stringResource(R.string.field_color), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                COLORS.forEachIndexed { i, c ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .clickable { colorIndex = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (i == colorIndex) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    }
                }
            }

            Text(stringResource(R.string.field_style), style = MaterialTheme.typography.titleSmall)
            CoinStyle.entries.forEach { option ->
                val selected = option == style
                Card(
                    onClick = { style = option },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(styleLabel(option)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(styleDescription(option)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.listing_includes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("• " + stringResource(R.string.include_certificate), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "• " + stringResource(R.string.include_founder, formatUsd(Listing.FOUNDER_ALLOCATION_USD)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("• " + stringResource(R.string.include_thin), style = MaterialTheme.typography.bodyMedium)
                    if (limitReached) {
                        Text(
                            stringResource(R.string.err_limit, Listing.MAX_COINS),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun styleDescription(style: CoinStyle): Int = when (style) {
    CoinStyle.STABLE -> R.string.style_stable_desc
    CoinStyle.MEME -> R.string.style_meme_desc
    CoinStyle.MOON -> R.string.style_moon_desc
}
