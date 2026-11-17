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
        val staked = Staking.accrue(current, min(elapsed, Staking.MAX_OFFLINE_SECONDS).toDouble())
        val withIncome = staked.copy(
            cash = current.cash + mined,
            lifetimeEarned = current.lifetimeEarned + mined,
        )
        _state.value = PriceEngine.catchUp(withIncome, elapsed, now, random).copy(savedAt = now)
        return OfflineReport(elapsed, mined)
    }

    /** One step of the live market. [seconds] is the real time since the previous tick. */
    fun tick(seconds: Double) {
        val now = clock()
        val earned = Clicker.earnPassive(Staking.accrue(_state.value, seconds), seconds)
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

    fun buyFarm(index: Int): Boolean {
        val next = Clicker.buyFarm(_state.value, index) ?: return false
        _state.value = next
        return true
    }

    fun stake(coinId: String, amount: Double): Boolean {
        val next = Staking.stake(_state.value, coinId, amount) ?: return false
        _state.value = next
        return true
    }

    fun unstake(coinId: String, amount: Double): Boolean {
        val next = Staking.unstake(_state.value, coinId, amount) ?: return false
        _state.value = next
        return true
    }

    fun mintNft(collectionId: String): MintResult {
        val (next, result) = Nfts.mint(_state.value, collectionId, clock(), random)
        if (result is MintResult.Minted) _state.value = next
        return result
    }

    /** Sells an NFT and returns what you received, or null if you do not own it. */
    fun sellNft(nftId: Long): Double? {
        val (next, proceeds) = Nfts.sell(_state.value, nftId)
        if (proceeds != null) _state.value = next
        return proceeds
    }

    fun placeOrder(coinId: String, type: OrderType, trigger: Double, amount: Double): OrderResult {
        val (next, result) = Orders.place(_state.value, coinId, type, trigger, amount, clock())
        if (result is OrderResult.Placed) _state.value = next
        return result
    }

    fun cancelOrder(orderId: Long) {
        _state.value = Orders.cancel(_state.value, orderId)
    }

    fun startMarketing(coinId: String, tier: MarketingTier): Boolean {
        val next = CoinManagement.startMarketing(_state.value, coinId, tier) ?: return false
        _state.value = next
        return true
    }

    fun burn(coinId: String, amount: Double): Boolean {
        val next = CoinManagement.burn(_state.value, coinId, amount) ?: return false
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

    fun send(coinId: String, amount: Double, to: String, ownAddresses: Set<String>): SendResult {
        val (next, result) = Transfers.send(_state.value, coinId, amount, to, ownAddresses, clock())
        if (result is SendResult.Ok) _state.value = next
        return result
    }

    fun swap(from: String, to: String, amount: Double): SwapResult {
        val (next, result) = Swaps.swap(_state.value, from, to, amount, clock())
        if (result is SwapResult.Ok) _state.value = next
        return result
    }

    fun quoteSwap(from: String, to: String, amount: Double): SwapQuote? = Swaps.quote(_state.value, from, to, amount)

    /** Claims the free airdrop if it is ready; returns what arrived. */
    fun claimAirdrop(): WalletTx? {
        val (next, tx) = Transfers.claimAirdrop(_state.value, clock(), random) ?: return null
        _state.value = next
        return tx
    }

    fun createCoin(
        name: String,
        ticker: String,
        icon: String,
        color: Long,
        style: CoinStyle,
        imageFile: String?,
    ): ListingResult {
        val (next, result) = Listing.create(_state.value, name, ticker, icon, color, style, clock(), imageFile)
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
