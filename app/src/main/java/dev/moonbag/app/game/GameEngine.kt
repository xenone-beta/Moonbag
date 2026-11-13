package dev.moonbag.app.game

import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What happened while the app was closed. */
data class OfflineReport(val seconds: Long, val earned: Double)

/**
 * Holds the game state and applies actions to it. Not thread-safe: call it from the main thread.
 */
class GameEngine(
    initial: GameState,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<GameState> = _state.asStateFlow()

    /** Runs the market and the miner for the time since the last save. Call once at start. */
    fun catchUp(): OfflineReport? {
        val now = clock()
        val current = _state.value
        if (current.savedAt <= 0L) return null
        val elapsed = ((now - current.savedAt) / 1000L).coerceIn(0L, PriceEngine.MAX_OFFLINE_SECONDS)
        if (elapsed < MIN_OFFLINE_SECONDS) return null

        val vaultSeconds = Clicker.vaultHours(current.upgrades) * 3600.0
        val mined = Clicker.perSecond(current.upgrades) * min(elapsed.toDouble(), vaultSeconds)
        val withIncome = current.copy(
            cash = current.cash + mined,
            lifetimeEarned = current.lifetimeEarned + mined,
        )
        _state.value = PriceEngine.catchUp(withIncome, elapsed, now, random).copy(savedAt = now)
        return OfflineReport(elapsed, mined)
    }

    /** One step of the live market. [seconds] is the real time since the previous tick. */
    fun tick(seconds: Double) {
        val now = clock()
        val earned = Clicker.earnPassive(_state.value, seconds)
        _state.value = PriceEngine.advance(earned, seconds / PriceEngine.TICK_SECONDS, now, random)
    }

    fun tap(): TapResult {
        val (next, result) = Clicker.tap(_state.value, random)
        _state.value = next
        return result
    }

    fun buyUpgrade(kind: UpgradeKind): Boolean {
        val next = Clicker.buy(_state.value, kind) ?: return false
        _state.value = next
        return true
    }

    fun buy(coinId: String, usd: Double): TradeResult {
        val (next, result) = Trading.buy(_state.value, coinId, usd, clock())
        if (result is TradeResult.Ok) _state.value = next
        return result
    }

    fun sell(coinId: String, amount: Double): TradeResult {
        val (next, result) = Trading.sell(_state.value, coinId, amount, clock())
        if (result is TradeResult.Ok) _state.value = next
        return result
    }

    fun createCoin(name: String, ticker: String, emoji: String, color: Long, style: CoinStyle): ListingResult {
        val (next, result) = Listing.create(_state.value, name, ticker, emoji, color, style, clock())
        if (result is ListingResult.Listed) _state.value = next
        return result
    }

    /** The state to write to disk, stamped with the current time. */
    fun snapshotForSave(): GameState {
        val stamped = _state.value.copy(savedAt = clock())
        _state.value = stamped
        return stamped
    }

    fun reset() {
        _state.value = GameState.new(clock(), random)
    }

    private companion object {
        const val MIN_OFFLINE_SECONDS = 60L
    }
}
