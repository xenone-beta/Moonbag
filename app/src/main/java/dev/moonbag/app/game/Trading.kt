package dev.moonbag.app.game

enum class TradeError { UNKNOWN_COIN, TOO_SMALL, NOT_ENOUGH_CASH, NOT_ENOUGH_COINS }

sealed interface TradeResult {
    data class Ok(val trade: Trade) : TradeResult
    data class Failed(val error: TradeError) : TradeResult
}

/** Market orders against the simulated price. Pure functions: they return the new state. */
object Trading {

    const val FEE_RATE = 0.005
    const val MIN_ORDER_USD = 0.01
    private const val MAX_TRADES = 100

    /** Spends [usd] (fee included) on [coinId]. Thin markets (your own coins) move with the order. */
    fun buy(state: GameState, coinId: String, usd: Double, now: Long): Pair<GameState, TradeResult> {
        val coin = state.coin(coinId) ?: return state to TradeResult.Failed(TradeError.UNKNOWN_COIN)
        if (!usd.isFinite() || usd < MIN_ORDER_USD) return state to TradeResult.Failed(TradeError.TOO_SMALL)
        if (usd > state.cash + 1e-9) return state to TradeResult.Failed(TradeError.NOT_ENOUGH_CASH)

        val price = state.price(coinId)
        val spend = minOf(usd, state.cash)
        val fee = spend * FEE_RATE
        val net = spend - fee
        val bought = Listing.coinsForUsd(price, coin.liquidity, net)
        val old = state.holdings[coinId] ?: Holding(0.0, 0.0)
        val trade = Trade(now, coinId, TradeSide.BUY, bought, net / bought, spend, fee, 0.0)
        val next = state.copy(
            cash = (state.cash - spend).coerceAtLeast(0.0),
            holdings = state.holdings + (coinId to Holding(old.amount + bought, old.costBasis + spend)),
            prices = state.prices + (coinId to Listing.priceAfterBuy(price, coin.liquidity, net)),
            feesPaid = state.feesPaid + fee,
            trades = (listOf(trade) + state.trades).take(MAX_TRADES),
        )
        return next to TradeResult.Ok(trade)
    }

    /** Sells [amount] coins of [coinId]. Selling (almost) everything closes the position. */
    fun sell(state: GameState, coinId: String, amount: Double, now: Long): Pair<GameState, TradeResult> {
        val coin = state.coin(coinId) ?: return state to TradeResult.Failed(TradeError.UNKNOWN_COIN)
        val held = state.holdings[coinId] ?: return state to TradeResult.Failed(TradeError.NOT_ENOUGH_COINS)
        if (!amount.isFinite() || amount <= 0.0) return state to TradeResult.Failed(TradeError.TOO_SMALL)
        if (amount > held.amount * (1 + 1e-9)) return state to TradeResult.Failed(TradeError.NOT_ENOUGH_COINS)

        val price = state.price(coinId)
        val sold = minOf(amount, held.amount)
        val gross = Listing.usdForCoins(price, coin.liquidity, sold)
        if (gross < MIN_ORDER_USD) return state to TradeResult.Failed(TradeError.TOO_SMALL)

        val fee = gross * FEE_RATE
        val proceeds = gross - fee
        val closing = sold >= held.amount * (1 - 1e-9)
        val removedBasis = if (closing) held.costBasis else held.costBasis * (sold / held.amount)
        val pnl = proceeds - removedBasis
        val holdings = if (closing) {
            state.holdings - coinId
        } else {
            state.holdings + (coinId to Holding(held.amount - sold, held.costBasis - removedBasis))
        }
        val trade = Trade(now, coinId, TradeSide.SELL, sold, gross / sold, proceeds, fee, pnl)
        val next = state.copy(
            cash = state.cash + proceeds,
            holdings = holdings,
            prices = state.prices + (coinId to Listing.priceAfterSell(price, coin.liquidity, sold)),
            realizedPnl = state.realizedPnl + pnl,
            feesPaid = state.feesPaid + fee,
            trades = (listOf(trade) + state.trades).take(MAX_TRADES),
        )
        return next to TradeResult.Ok(trade)
    }
}
