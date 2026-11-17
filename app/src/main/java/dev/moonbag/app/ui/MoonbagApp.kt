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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import dev.moonbag.app.ui.wallet.BrowserScreen
import dev.moonbag.app.ui.wallet.ContactsScreen
import dev.moonbag.app.ui.wallet.NetworksScreen
import dev.moonbag.app.ui.wallet.ReceiveScreen
import dev.moonbag.app.ui.wallet.SeedPhraseScreen
import dev.moonbag.app.ui.wallet.SendScreen
import dev.moonbag.app.ui.wallet.SwapScreen
import dev.moonbag.app.ui.wallet.WalletScreen
import dev.moonbag.app.ui.wallet.WalletSetupScreen
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
import dev.moonbag.app.ui.board.BoardScreen
import dev.moonbag.app.ui.tutorial.TutorialScreen
import dev.moonbag.app.ui.market.CertificateDialog
import dev.moonbag.app.ui.market.CoinDetailScreen
import dev.moonbag.app.ui.market.CreateCoinScreen
import dev.moonbag.app.ui.market.MarketScreen
import dev.moonbag.app.ui.mine.MineScreen
import dev.moonbag.app.ui.nft.NftScreen
import dev.moonbag.app.ui.portfolio.PortfolioScreen
import dev.moonbag.app.ui.settings.SettingsScreen

private enum class Tab(val label: Int, val on: ImageVector, val off: ImageVector) {
    WALLET(R.string.tab_wallet, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    MARKET(R.string.tab_market, Icons.Filled.ShowChart, Icons.Outlined.ShowChart),
    SWAP(R.string.swap, Icons.Filled.SwapHoriz, Icons.Outlined.SwapHoriz),
    BROWSER(R.string.tab_browser, Icons.Filled.Explore, Icons.Outlined.Explore),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun MoonbagApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    val haptics by viewModel.haptics.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val tutorialDone by viewModel.tutorialDone.collectAsStateWithLifecycle()
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    // null = tabs; otherwise a full-screen page: "create", "coin:<id>", "send[:<id>]", "receive", "setup", "seed", ...
    var overlay by rememberSaveable {
        mutableStateOf<String?>(
            when {
                !viewModel.tutorialDone.value -> "tutorial"
                !viewModel.wallet.value.exists -> "setup"
                else -> null
            },
        )
    }
    var certificateId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    BackHandler(enabled = overlay != null) {
        if (overlay == "tutorial") viewModel.finishTutorial()
        overlay = null
    }

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
                onCreate = { name, ticker, icon, color, style, imageFile ->
                    viewModel.createCoin(name, ticker, icon, color, style, imageFile)
                },
                onListed = { id ->
                    overlay = "coin:$id"
                    certificateId = id
                },
            )
        } else if (screen == "tutorial") {
            TutorialScreen(onFinish = { viewModel.finishTutorial(); overlay = if (wallet.exists) null else "setup" })
        } else if (screen == "setup") {
            WalletSetupScreen(
                wallet = wallet,
                onCreate = { viewModel.createWallet() },
                onImport = viewModel::importWallet,
                onBackedUp = viewModel::confirmBackup,
                onFinish = { overlay = null },
            )
        } else if (screen == "seed") {
            SeedPhraseScreen(wallet, onConfirmBackup = viewModel::confirmBackup, onBack = { overlay = null })
        } else if (screen == "networks") {
            NetworksScreen(state, wallet, onBack = { overlay = null })
        } else if (screen == "contacts") {
            ContactsScreen(contacts, viewModel::addContact, viewModel::removeContact, onBack = { overlay = null })
        } else if (screen == "receive") {
            ReceiveScreen(state, wallet, onClaimAirdrop = viewModel::claimAirdrop, onSetup = { overlay = "setup" }, onBack = { overlay = null })
        } else if (screen != null && screen.startsWith("send")) {
            SendScreen(
                state = state,
                wallet = wallet,
                contacts = contacts,
                startCoinId = screen.removePrefix("send").removePrefix(":").ifEmpty { null },
                onSend = viewModel::send,
                onBack = { overlay = null },
            )
        } else if (screen == "mine") {
            Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                MineScreen(
                    state = state,
                    haptics = haptics,
                    onTap = viewModel::tap,
                    onBuyUpgrade = viewModel::buyUpgrade,
                    onBuyFarm = viewModel::buyFarm,
                )
            }
        } else if (screen == "nft") {
            Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                NftScreen(state = state, onMint = viewModel::mintNft, onSell = viewModel::sellNft)
            }
        } else if (screen == "stats") {
            Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                PortfolioScreen(
                    state = state,
                    onOpenCoin = { overlay = "coin:$it" },
                    onGoMarket = { tabIndex = Tab.MARKET.ordinal; overlay = null },
                    onOpenBoard = { overlay = "board" },
                    onCancelOrder = viewModel::cancelOrder,
                )
            }
        } else if (screen == "board") {
            BoardScreen(state = state, onBack = { overlay = null })
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
                onStake = { amount -> viewModel.stake(coinId, amount) },
                onUnstake = { amount -> viewModel.unstake(coinId, amount) },
                onPlaceOrder = { type, trigger, amount -> viewModel.placeOrder(coinId, type, trigger, amount) },
                onCancelOrder = viewModel::cancelOrder,
                onMarketing = { tier -> viewModel.startMarketing(coinId, tier) },
                onBurn = { amount -> viewModel.burn(coinId, amount) },
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
                        Tab.WALLET -> WalletScreen(
                            state = state,
                            wallet = wallet,
                            onSend = { overlay = "send" },
                            onReceive = { overlay = "receive" },
                            onSwap = { tabIndex = Tab.SWAP.ordinal },
                            onEarn = { overlay = "mine" },
                            onOpenCoin = { overlay = "coin:$it" },
                            onAnalytics = { overlay = "stats" },
                            onSetup = { overlay = "setup" },
                            onBackup = { overlay = "seed" },
                        )
                        Tab.MARKET -> MarketScreen(
                            state = state,
                            onOpenCoin = { overlay = "coin:$it" },
                            onGoMine = { overlay = "mine" },
                            onCreateCoin = { overlay = "create" },
                        )
                        Tab.SWAP -> SwapScreen(
                            state = state,
                            onSwap = viewModel::swap,
                            onEarn = { overlay = "mine" },
                        )
                        Tab.BROWSER -> BrowserScreen(
                            onOpen = { id ->
                                when (id) {
                                    "swap" -> tabIndex = Tab.SWAP.ordinal
                                    "mint" -> overlay = "nft"
                                    "mine" -> overlay = "mine"
                                    "launch" -> overlay = "create"
                                    "airdrop" -> overlay = "receive"
                                    "board" -> overlay = "board"
                                    "stats" -> overlay = "stats"
                                    "networks" -> overlay = "networks"
                                }
                            },
                        )
                        Tab.SETTINGS -> SettingsScreen(
                            state = state,
                            wallet = wallet,
                            dynamicColor = dynamicColor,
                            haptics = haptics,
                            onDynamicColor = viewModel::setDynamicColor,
                            onHaptics = viewModel::setHaptics,
                            onReset = viewModel::resetGame,
                            onOpenTutorial = { overlay = "tutorial" },
                            onOpenSeed = { overlay = "seed" },
                            onOpenNetworks = { overlay = "networks" },
                            onOpenContacts = { overlay = "contacts" },
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
