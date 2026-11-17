package dev.moonbag.app.game

enum class SwapError { SAME_COIN, TRADE }

sealed interface SwapResult {
    data class Ok(val tx: WalletTx) : SwapResult
    data class Failed(val error: SwapError, val trade: TradeError? = null) : SwapResult
}

/** What a swap would give, for showing before the user confirms. */
data class SwapQuote(val toAmount: Double, val feeUsd: Double, val fromValue: Double, val toValue: Double) {
    /** Share of the value lost to fees and price impact (0.012 = 1.2 percent). */
    val loss: Double get() = if (fromValue > 0) (1.0 - toValue / fromValue).coerceAtLeast(0.0) else 0.0
}

/** Swapping one coin for another: sell into play dollars and buy the other, in one step. */
object Swaps {

    fun swap(state: GameState, from: String, to: String, amount: Double, now: Long): Pair<GameState, SwapResult> {
        if (from == to) return state to SwapResult.Failed(SwapError.SAME_COIN)
        val (afterSell, sold) = Trading.sell(state, from, amount, now)
        if (sold !is TradeResult.Ok) return state to SwapResult.Failed(SwapError.TRADE, (sold as TradeResult.Failed).error)
        val proceeds = sold.trade.usd
        val (afterBuy, bought) = Trading.buy(afterSell, to, proceeds, now)
        if (bought !is TradeResult.Ok) return state to SwapResult.Failed(SwapError.TRADE, (bought as TradeResult.Failed).error)

        val fee = sold.trade.fee + bought.trade.fee
        val toCoin = state.coin(to) ?: return state to SwapResult.Failed(SwapError.TRADE, TradeError.UNKNOWN_COIN)
        val tx = WalletTx(
            now, TxKind.SWAP, from, sold.trade.amount,
            toCoinId = to, toAmount = bought.trade.amount, feeUsd = fee, valueUsd = proceeds,
            chainId = Chains.of(toCoin).id,
        )
        // The swap is one line in the activity list, not a sale and a purchase.
        return Transfers.addTx(afterBuy.copy(trades = state.trades), tx) to SwapResult.Ok(tx)
    }

    fun quote(state: GameState, from: String, to: String, amount: Double): SwapQuote? {
        val result = swap(state, from, to, amount, 0L).second
        if (result !is SwapResult.Ok) return null
        val fromValue = result.tx.amount * state.price(from)
        // Value at the price the market had before the swap moved it.
        val toValue = result.tx.toAmount * state.price(to)
        return SwapQuote(result.tx.toAmount, result.tx.feeUsd, fromValue, toValue)
    }
}
