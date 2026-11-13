package dev.moonbag.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.moonbag.app.MainViewModel
import dev.moonbag.app.R
import dev.moonbag.app.ui.market.CertificateDialog
import dev.moonbag.app.ui.market.CoinDetailScreen
import dev.moonbag.app.ui.market.CreateCoinScreen
import dev.moonbag.app.ui.market.MarketScreen
import dev.moonbag.app.ui.mine.MineScreen
import dev.moonbag.app.ui.portfolio.PortfolioScreen
import dev.moonbag.app.ui.settings.SettingsScreen

private enum class Tab(val label: Int, val on: ImageVector, val off: ImageVector) {
    MARKET(R.string.tab_market, Icons.Filled.ShowChart, Icons.Outlined.ShowChart),
    PORTFOLIO(R.string.tab_portfolio, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    MINE(R.string.tab_mine, Icons.Filled.Bolt, Icons.Outlined.Bolt),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun MoonbagApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    val haptics by viewModel.haptics.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    // null = tabs, "create" = new-coin form, "coin:<id>" = coin page
    var overlay by rememberSaveable { mutableStateOf<String?>(null) }
    var certificateId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    BackHandler(enabled = overlay != null) { overlay = null }

    AnimatedContent(
        targetState = overlay,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280))) togetherWith
                    fadeOut(tween(160))
            } else {
                fadeIn(tween(220)) togetherWith
                    (slideOutHorizontally(tween(280)) { it / 4 } + fadeOut(tween(200)))
            }
        },
        label = "coin-detail",
    ) { screen ->
        if (screen == "create") {
            CreateCoinScreen(
                state = state,
                onBack = { overlay = null },
                onCreate = { name, ticker, emoji, color, style ->
                    viewModel.createCoin(name, ticker, emoji, color, style)
                },
                onListed = { id ->
                    overlay = "coin:$id"
                    certificateId = id
                },
            )
        } else if (screen != null && screen.startsWith("coin:")) {
            val coinId = screen.removePrefix("coin:")
            CoinDetailScreen(
                coinId = coinId,
                state = state,
                snackbar = snackbar,
                onBack = { overlay = null },
                onBuy = { usd -> viewModel.buy(coinId, usd) },
                onSell = { amount -> viewModel.sell(coinId, amount) },
                onCertificate = { certificateId = coinId },
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    NavigationBar {
                        Tab.entries.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = tabIndex == index,
                                onClick = { tabIndex = index },
                                icon = {
                                    Icon(if (tabIndex == index) tab.on else tab.off, contentDescription = null)
                                },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
                },
            ) { padding ->
                val content = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding())
                AnimatedContent(
                    targetState = tabIndex,
                    modifier = content,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                    label = "tabs",
                ) { index ->
                    when (Tab.entries[index]) {
                        Tab.MARKET -> MarketScreen(
                            state = state,
                            onOpenCoin = { overlay = "coin:$it" },
                            onGoMine = { tabIndex = Tab.MINE.ordinal },
                            onCreateCoin = { overlay = "create" },
                        )
                        Tab.PORTFOLIO -> PortfolioScreen(
                            state = state,
                            onOpenCoin = { overlay = "coin:$it" },
                            onGoMarket = { tabIndex = Tab.MARKET.ordinal },
                        )
                        Tab.MINE -> MineScreen(
                            state = state,
                            haptics = haptics,
                            onTap = viewModel::tap,
                            onBuyUpgrade = viewModel::buyUpgrade,
                        )
                        Tab.SETTINGS -> SettingsScreen(
                            state = state,
                            dynamicColor = dynamicColor,
                            haptics = haptics,
                            onDynamicColor = viewModel::setDynamicColor,
                            onHaptics = viewModel::setHaptics,
                            onReset = viewModel::resetGame,
                        )
                    }
                }
            }
        }
    }

    certificateId?.let { id ->
        val coin = state.customCoins.firstOrNull { it.id == id }
        if (coin != null) CertificateDialog(coin, onDismiss = { certificateId = null })
    }

    offline?.let { report ->
        AlertDialog(
            onDismissRequest = viewModel::dismissOffline,
            title = { Text(stringResource(R.string.welcome_back)) },
            text = {
                Text(
                    stringResource(
                        R.string.welcome_back_body,
                        formatDuration(report.seconds),
                        formatUsd(report.earned),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissOffline) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

/** 8130 -> "2h 15m", 300 -> "5m" (units come from the string resources). */
@Composable
fun formatDuration(totalSeconds: Long): String {
    val days = (totalSeconds / 86_400).toInt()
    val hours = (totalSeconds % 86_400 / 3600).toInt()
    val minutes = (totalSeconds % 3600 / 60).toInt()
    return when {
        days > 0 -> stringResource(R.string.duration_dh, days, hours)
        hours > 0 -> stringResource(R.string.duration_hm, hours, minutes)
        else -> stringResource(R.string.duration_m, minutes.coerceAtLeast(1))
    }
}
