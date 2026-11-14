package dev.moonbag.app.game

enum class OrderType { LIMIT_BUY, LIMIT_SELL, STOP_LOSS }

/**
 * A standing order. [amount] is dollars for a limit buy (reserved when the order is placed) and coins
 * for the sell types. [trigger] is the price that sets it off.
 */
data class Order(
    val id: Long,
    val coinId: String,
    val type: OrderType,
    val trigger: Double,
    val amount: Double,
    val createdAt: Long,
)

/** A record of an order that went off. */
data class OrderFill(val time: Long, val coinId: String, val type: OrderType, val price: Double, val amount: Double)

enum class OrderError { UNKNOWN_COIN, BAD_PRICE, BAD_AMOUNT, NOT_ENOUGH_CASH, NOT_ENOUGH_COINS, TOO_MANY }

sealed interface OrderResult {
    data class Placed(val order: Order) : OrderResult
    data class Failed(val error: OrderError) : OrderResult
}

/** Limit buys, limit sells and stop losses. They are checked on every market step, also while offline. */
object Orders {

    const val MAX_OPEN = 20
    private const val MAX_FILLS = 20

    fun place(
        state: GameState,
        coinId: String,
        type: OrderType,
        trigger: Double,
        amount: Double,
        now: Long,
    ): Pair<GameState, OrderResult> {
        fun fail(e: OrderError) = state to OrderResult.Failed(e)

        if (state.coin(coinId) == null) return fail(OrderError.UNKNOWN_COIN)
        if (state.orders.size >= MAX_OPEN) return fail(OrderError.TOO_MANY)
        val price = state.price(coinId)
        if (!trigger.isFinite() || trigger <= 0.0) return fail(OrderError.BAD_PRICE)
        if (!amount.isFinite() || amount <= 0.0) return fail(OrderError.BAD_AMOUNT)
        when (type) {
            OrderType.LIMIT_BUY -> {
                if (trigger >= price) return fail(OrderError.BAD_PRICE)
                if (amount < Trading.MIN_ORDER_USD) return fail(OrderError.BAD_AMOUNT)
                if (amount > state.cash + 1e-9) return fail(OrderError.NOT_ENOUGH_CASH)
            }
            OrderType.LIMIT_SELL -> {
                if (trigger <= price) return fail(OrderError.BAD_PRICE)
                if (amount > (state.holdings[coinId]?.liquid ?: 0.0) * (1 + 1e-9)) return fail(OrderError.NOT_ENOUGH_COINS)
            }
            OrderType.STOP_LOSS -> {
                if (trigger >= price) return fail(OrderError.BAD_PRICE)
                if (amount > (state.holdings[coinId]?.liquid ?: 0.0) * (1 + 1e-9)) return fail(OrderError.NOT_ENOUGH_COINS)
            }
        }
        val order = Order(state.nextOrderId, coinId, type, trigger, amount, now)
        val reserved = if (type == OrderType.LIMIT_BUY) minOf(amount, state.cash) else 0.0
        val next = state.copy(
            cash = (state.cash - reserved).coerceAtLeast(0.0),
            orders = state.orders + order,
            nextOrderId = state.nextOrderId + 1,
        )
        return next to OrderResult.Placed(order)
    }

    /** Removes an order. A limit buy gives its reserved dollars back. */
    fun cancel(state: GameState, orderId: Long): GameState {
        val order = state.orders.firstOrNull { it.id == orderId } ?: return state
        val refund = if (order.type == OrderType.LIMIT_BUY) order.amount else 0.0
        return state.copy(cash = state.cash + refund, orders = state.orders.filterNot { it.id == orderId })
    }

    private fun triggered(order: Order, price: Double): Boolean = when (order.type) {
        OrderType.LIMIT_BUY, OrderType.STOP_LOSS -> price <= order.trigger
        OrderType.LIMIT_SELL -> price >= order.trigger
    }

    /** Runs every order whose price has been reached. Fills happen at the current price, not the trigger. */
    fun process(state: GameState, now: Long): GameState {
        if (state.orders.isEmpty()) return state
        var s = state
        for (order in state.orders) {
            val price = s.price(order.coinId)
            if (!triggered(order, price)) continue
            s = s.copy(orders = s.orders.filterNot { it.id == order.id })
            var executed = false
            s = when (order.type) {
                OrderType.LIMIT_BUY -> {
                    val funded = s.copy(cash = s.cash + order.amount)
                    val (bought, result) = Trading.buy(funded, order.coinId, order.amount, now)
                    if (result is TradeResult.Ok) { executed = true; bought } else funded
                }
                OrderType.LIMIT_SELL, OrderType.STOP_LOSS -> {
                    val liquid = s.holdings[order.coinId]?.liquid ?: 0.0
                    val coins = minOf(order.amount, liquid)
                    if (coins <= 0.0) s else {
                        val (sold, result) = Trading.sell(s, order.coinId, coins, now)
                        if (result is TradeResult.Ok) { executed = true; sold } else s
                    }
                }
            }
            if (executed) {
                s = s.copy(orderFills = (listOf(OrderFill(now, order.coinId, order.type, price, order.amount)) + s.orderFills).take(MAX_FILLS))
            }
        }
        return s
    }
}
