package dev.moonbag.app.ui.market

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.moonbag.app.R
import dev.moonbag.app.game.CoinStyle
import dev.moonbag.app.game.CustomCoin
import dev.moonbag.app.game.Listing
import dev.moonbag.app.ui.components.CoinAvatar
import dev.moonbag.app.ui.formatDate

fun styleLabel(style: CoinStyle): Int = when (style) {
    CoinStyle.STABLE -> R.string.style_stable
    CoinStyle.MEME -> R.string.style_meme
    CoinStyle.MOON -> R.string.style_moon
}

/** The listing certificate of a coin you created. Purely decorative: a keepsake for the game. */
@Composable
fun CertificateDialog(coin: CustomCoin, onDismiss: () -> Unit) {
    val gold = Brush.linearGradient(listOf(Color(0xFFF6D77A), Color(0xFFB8862B), Color(0xFFF6D77A)))
    val tint = Color(coin.color)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(3.dp, gold),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.cert_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center,
                    color = Color(0xFFB8862B),
                )
                HorizontalDivider(Modifier.fillMaxWidth(0.5f), color = Color(0xFFB8862B).copy(alpha = 0.5f))

                CoinAvatar(coin.toDef(), size = 84.dp, modifier = Modifier.padding(top = 6.dp))

                Text(
                    stringResource(R.string.cert_certifies),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    coin.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.cert_body, coin.name, coin.symbol),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(4.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CertRow(stringResource(R.string.cert_number), Listing.certificateNumber(coin.serial), mono = true)
                    CertRow(stringResource(R.string.cert_date), formatDate(coin.createdAt))
                    CertRow(stringResource(R.string.cert_style), stringResource(styleLabel(coin.style)))
                }

                Text(
                    stringResource(R.string.cert_footer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cert_close)) }
            }
        }
    }
}

@Composable
private fun CertRow(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}
