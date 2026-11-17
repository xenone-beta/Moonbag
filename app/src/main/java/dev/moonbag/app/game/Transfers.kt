package dev.moonbag.app.game

import kotlin.random.Random

enum class TxKind { SEND, RECEIVE, SWAP, AIRDROP }

/** One line of the wallet's activity list. */
data class WalletTx(
    val time: Long,
    val kind: TxKind,
    val coinId: String,
    val amount: Double,
    /** The other side's address for sends; empty otherwise. */
    val counterparty: String = "",
    /** For swaps: what came in. */
    val toCoinId: String = "",
    val toAmount: Double = 0.0,
    val feeUsd: Double = 0.0,
    /** Worth of the coins at the moment, in play dollars. */
    val valueUsd: Double = 0.0,
    val chainId: String = "",
)

enum class SendError { UNKNOWN_COIN, BAD_AMOUNT, NOT_ENOUGH_COINS, BAD_ADDRESS, OWN_ADDRESS, NOT_ENOUGH_FEE }

sealed interface SendResult {
    data class Ok(val tx: WalletTx) : SendResult
    data class Failed(val error: SendError) : SendResult
}

/** Sending coins away and receiving the free ones. All functions are pure and return the new state. */
object Transfers {

    private const val MAX_TXS = 100
    const val AIRDROP_COOLDOWN_MS = 6L * 3600 * 1000
    private const val AIRDROP_MIN_USD = 5.0
    private const val AIRDROP_MAX_USD = 25.0

    fun addTx(state: GameState, tx: WalletTx): GameState =
        state.copy(walletTxs = (listOf(tx) + state.walletTxs).take(MAX_TXS))

    /** Sends [amount] of [coinId] to [to]. The fee is paid in play dollars from [GameState.cash]. */
    fun send(
        state: GameState,
        coinId: String,
        amount: Double,
        to: String,
        ownAddresses: Set<String>,
        now: Long,
    ): Pair<GameState, SendResult> {
        fun fail(e: SendError) = state to SendResult.Failed(e)
        val coin = state.coin(coinId) ?: return fail(SendError.UNKNOWN_COIN)
        val chain = Chains.of(coin)
        if (!amount.isFinite() || amount <= 0.0) return fail(SendError.BAD_AMOUNT)
        val held = state.holdings[coinId] ?: return fail(SendError.NOT_ENOUGH_COINS)
        if (amount > held.liquid * (1 + 1e-9)) return fail(SendError.NOT_ENOUGH_COINS)
        if (!Wallet.isAddress(chain, to)) return fail(SendError.BAD_ADDRESS)
        if (Wallet.canonical(to) in ownAddresses) return fail(SendError.OWN_ADDRESS)
        if (state.cash + 1e-9 < chain.feeUsd) return fail(SendError.NOT_ENOUGH_FEE)

        val sent = minOf(amount, held.liquid)
        val closing = held.staked <= 0.0 && sent >= held.amount * (1 - 1e-9)
        val removedBasis = if (closing) held.costBasis else held.costBasis * (sent / held.amount)
        val holdings = if (closing) {
            state.holdings - coinId
        } else {
            state.holdings + (coinId to Holding(held.amount - sent, held.costBasis - removedBasis, held.staked))
        }
        val value = sent * state.price(coinId)
        val tx = WalletTx(now, TxKind.SEND, coinId, sent, Wallet.canonical(to), feeUsd = chain.feeUsd, valueUsd = value, chainId = chain.id)
        val next = state.copy(
            cash = (state.cash - chain.feeUsd).coerceAtLeast(0.0),
            holdings = holdings,
            feesPaid = state.feesPaid + chain.feeUsd,
            // What leaves as a gift is not a trading loss.
            granted = state.granted - value,
        )
        return addTx(next, tx) to SendResult.Ok(tx)
    }

    /** Milliseconds until the next free airdrop can be claimed; 0 when it is ready. */
    fun airdropWait(state: GameState, now: Long): Long =
        (state.lastAirdropAt + AIRDROP_COOLDOWN_MS - now).coerceIn(0L, AIRDROP_COOLDOWN_MS)

    /** A free gift of a random coin, once every six hours. Returns null while it is not ready. */
    fun claimAirdrop(state: GameState, now: Long, random: Random): Pair<GameState, WalletTx>? {
        if (state.lastAirdropAt > 0 && airdropWait(state, now) > 0) return null
        val coin = Coins.all[random.nextInt(Coins.all.size)]
        val price = state.price(coin.id)
        if (price <= 0.0) return null
        val value = AIRDROP_MIN_USD + random.nextDouble() * (AIRDROP_MAX_USD - AIRDROP_MIN_USD)
        val amount = value / price
        val old = state.holdings[coin.id] ?: Holding(0.0, 0.0)
        val tx = WalletTx(now, TxKind.AIRDROP, coin.id, amount, valueUsd = value, chainId = Chains.of(coin).id)
        val next = state.copy(
            holdings = state.holdings + (coin.id to Holding(old.amount + amount, old.costBasis + value, old.staked)),
            granted = state.granted + value,
            lastAirdropAt = now,
        )
        return addTx(next, tx) to tx
    }
}
