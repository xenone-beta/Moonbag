package dev.moonbag.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.viewModelScope
import dev.moonbag.app.game.CoinStyle
import dev.moonbag.app.game.GameEngine
import dev.moonbag.app.game.GameState
import dev.moonbag.app.game.GameStore
import dev.moonbag.app.game.ListingResult
import dev.moonbag.app.game.MarketingTier
import dev.moonbag.app.game.MintResult
import dev.moonbag.app.game.OrderResult
import dev.moonbag.app.game.OrderType
import dev.moonbag.app.game.OfflineReport
import dev.moonbag.app.game.PriceEngine
import dev.moonbag.app.game.TapResult
import dev.moonbag.app.game.TradeResult
import dev.moonbag.app.ui.widget.PortfolioWidget
import dev.moonbag.app.game.UpgradeKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = GameStore(app)
    private val engine = GameEngine(store.load() ?: GameState.new(System.currentTimeMillis()))

    val state: StateFlow<GameState> = engine.state

    private val _offline = MutableStateFlow<OfflineReport?>(null)
    /** Set once when the app opens after a long break; cleared by [dismissOffline]. */
    val offline: StateFlow<OfflineReport?> = _offline.asStateFlow()

    private val _dynamicColor = MutableStateFlow(store.dynamicColor)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _haptics = MutableStateFlow(store.haptics)
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()

    private val _tutorialDone = MutableStateFlow(store.tutorialDone)
    val tutorialDone: StateFlow<Boolean> = _tutorialDone.asStateFlow()

    private var loop: Job? = null

    /** Called when the screen becomes visible: simulates the time away, then starts the live market. */
    fun onStart() {
        engine.catchUp()?.let { report ->
            if (report.earned >= 0.01 || report.seconds >= OFFLINE_NOTICE_SECONDS) _offline.value = report
        }
        loop?.cancel()
        loop = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            var ticks = 0
            while (isActive) {
                delay((PriceEngine.TICK_SECONDS * 1000).toLong())
                val now = SystemClock.elapsedRealtime()
                engine.tick((now - last) / 1000.0)
                last = now
                if (++ticks % SAVE_EVERY_TICKS == 0) store.save(engine.snapshotForSave())
            }
        }
    }

    /** Called when the screen is hidden: stops the loop and saves. */
    fun onStop() {
        loop?.cancel()
        loop = null
        store.save(engine.snapshotForSave())
        viewModelScope.launch { PortfolioWidget().updateAll(getApplication<Application>()) }
    }

    fun tap(): TapResult = engine.tap()

    fun buyUpgrade(kind: UpgradeKind): Boolean = engine.buyUpgrade(kind)

    fun placeOrder(coinId: String, type: OrderType, trigger: Double, amount: Double): OrderResult =
        engine.placeOrder(coinId, type, trigger, amount)

    fun cancelOrder(orderId: Long) = engine.cancelOrder(orderId)

    fun startMarketing(coinId: String, tier: MarketingTier): Boolean = engine.startMarketing(coinId, tier)

    fun burn(coinId: String, amount: Double): Boolean = engine.burn(coinId, amount)

    fun buyFarm(index: Int): Boolean = engine.buyFarm(index)

    fun stake(coinId: String, amount: Double): Boolean = engine.stake(coinId, amount)

    fun unstake(coinId: String, amount: Double): Boolean = engine.unstake(coinId, amount)

    fun mintNft(collectionId: String): MintResult = engine.mintNft(collectionId)

    fun sellNft(nftId: Long): Double? = engine.sellNft(nftId)

    fun buy(coinId: String, usd: Double): TradeResult = engine.buy(coinId, usd)

    fun sell(coinId: String, amount: Double): TradeResult = engine.sell(coinId, amount)

    fun createCoin(
        name: String,
        ticker: String,
        icon: String,
        color: Long,
        style: CoinStyle,
        imageFile: String?,
    ): ListingResult {
        val result = engine.createCoin(name, ticker, icon, color, style, imageFile)
        if (result is ListingResult.Listed) store.save(engine.snapshotForSave())
        return result
    }

    fun dismissOffline() {
        _offline.value = null
    }

    fun finishTutorial() {
        store.tutorialDone = true
        _tutorialDone.value = true
    }

    fun setDynamicColor(value: Boolean) {
        store.dynamicColor = value
        _dynamicColor.value = value
    }

    fun setHaptics(value: Boolean) {
        store.haptics = value
        _haptics.value = value
    }

    fun resetGame() {
        CoinImages.clear(getApplication())
        engine.reset()
        _offline.value = null
        store.save(engine.snapshotForSave())
    }

    private companion object {
        const val SAVE_EVERY_TICKS = 8
        const val OFFLINE_NOTICE_SECONDS = 300L
    }
}
