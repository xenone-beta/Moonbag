package dev.moonbag.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.moonbag.app.MainActivity
import dev.moonbag.app.R
import dev.moonbag.app.game.GameState
import dev.moonbag.app.game.GameStore
import dev.moonbag.app.ui.formatPercent
import dev.moonbag.app.ui.formatSignedUsd
import dev.moonbag.app.ui.formatTime
import dev.moonbag.app.ui.formatUsd

private val UpColor = ColorProvider(Color(0xFF2E9E6B))
private val DownColor = ColorProvider(Color(0xFFD64560))

/**
 * Home screen widget with the portfolio as of the last time the app was closed. It shows the saved
 * numbers as they are: the market only moves while the app is open or when you come back to it.
 */
class PortfolioWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = GameStore(context).load()
        provideContent {
            GlanceTheme { WidgetContent(state) }
        }
    }
}

@Composable
private fun WidgetContent(state: GameState?) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            text = context.getString(R.string.app_name),
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
        if (state == null) {
            Text(
                text = context.getString(R.string.widget_empty),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
            return@Column
        }

        val profit = state.totalProfit()
        Text(
            text = formatUsd(state.netWorth()),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = formatSignedUsd(profit),
            style = TextStyle(color = if (profit >= 0) UpColor else DownColor, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(GlanceModifier.height(8.dp))

        val top = state.holdings.entries
            .sortedByDescending { (id, h) -> h.amount * state.price(id) }
            .take(3)
        top.forEach { (id, h) ->
            val coin = state.coin(id) ?: return@forEach
            val change = state.change(id)
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = coin.symbol,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    text = formatUsd(h.amount * state.price(id)) + "  ",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
                Text(
                    text = formatPercent(change),
                    style = TextStyle(color = if (change >= 0) UpColor else DownColor, fontSize = 12.sp),
                )
            }
        }
        if (top.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_no_coins),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = context.getString(R.string.widget_as_of, formatTime(state.savedAt)),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
        )
    }
}
