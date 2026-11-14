package dev.moonbag.app.game

import kotlin.math.pow

/** How a coin you create behaves. */
enum class CoinStyle(
    val startPrice: Double,
    val volatility: Double,
    val jumpChance: Double,
    val rugChance: Double,
    val beta: Double,
    val risk: Int,
    /** Pool depth in dollars: the lower it is, the more your own trades move the price. */
    val liquidity: Double,
) {
    STABLE(1.0, 0.006, 0.0006, 0.0, 0.8, 1, 40_000.0),
    MEME(0.01, 0.014, 0.0020, 0.0, 1.3, 3, 20_000.0),
    MOON(0.0001, 0.028, 0.0050, 0.0001, 2.0, 4, 8_000.0),
}

/** A coin created by the player. Saved with the game; [toDef] turns it into a tradable coin. */
data class CustomCoin(
    val id: String,
    val symbol: String,
    val name: String,
    val icon: String,
    val color: Long,
    val style: CoinStyle,
    val serial: Int,
    val createdAt: Long,
    val imageFile: String? = null,
) {
    fun toDef(): CoinDef = CoinDef(
        id = id,
        symbol = symbol,
        name = name,
        icon = icon,
        color = color,
        startPrice = style.startPrice,
        supply = SUPPLY,
        volatility = style.volatility,
        jumpChance = style.jumpChance,
        rugChance = style.rugChance,
        beta = style.beta,
        risk = style.risk,
        kind = CoinKind.CUSTOM,
        liquidity = style.liquidity,
        serial = serial,
        createdAt = createdAt,
        imageFile = imageFile,
    )

    companion object {
        const val SUPPLY = 1e9
    }
}

enum class ListingError { BAD_NAME, BAD_TICKER, TICKER_TAKEN, LIMIT_REACHED, NOT_ENOUGH_CASH }

sealed interface ListingResult {
    data class Listed(val coin: CustomCoin) : ListingResult
    data class Failed(val error: ListingError) : ListingResult
}

/** Launching your own coin: a $2,000 listing fee buys a certificate and a founder allocation. */
object Listing {

    const val FEE = 2000.0
    const val MAX_COINS = 5
    /** Value of the coins the founder gets at the starting price. */
    const val FOUNDER_ALLOCATION_USD = 1000.0
    const val MIN_NAME = 2
    const val MAX_NAME = 20
    const val MIN_TICKER = 2
    const val MAX_TICKER = 5

    /** Upper-case letters and digits only. */
    fun cleanTicker(input: String): String =
        input.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(MAX_TICKER)

    fun create(
        state: GameState,
        name: String,
        ticker: String,
        icon: String,
        color: Long,
        style: CoinStyle,
        now: Long,
        imageFile: String? = null,
    ): Pair<GameState, ListingResult> {
        fun fail(e: ListingError) = state to ListingResult.Failed(e)

        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        val symbol = cleanTicker(ticker)
        if (cleanName.length < MIN_NAME || cleanName.length > MAX_NAME) return fail(ListingError.BAD_NAME)
        if (symbol.length < MIN_TICKER || symbol.none { it in 'A'..'Z' }) return fail(ListingError.BAD_TICKER)
        if (state.allCoins.any { it.symbol == symbol }) return fail(ListingError.TICKER_TAKEN)
        if (state.customCoins.size >= MAX_COINS) return fail(ListingError.LIMIT_REACHED)
        if (state.cash + 1e-9 < FEE) return fail(ListingError.NOT_ENOUGH_CASH)

        val serial = state.coinsListed + 1
        val coin = CustomCoin(
            id = "c$serial",
            symbol = symbol,
            name = cleanName,
            icon = icon.ifBlank { "star" },
            color = color,
            style = style,
            serial = serial,
            createdAt = now,
            imageFile = imageFile,
        )
        val price = style.startPrice
        val allocation = FOUNDER_ALLOCATION_USD / price
        val next = state.copy(
            cash = (state.cash - FEE).coerceAtLeast(0.0),
            customCoins = state.customCoins + coin,
            coinsListed = serial,
            prices = state.prices + (coin.id to price),
            history = state.history + (coin.id to listOf(price)),
            holdings = state.holdings + (coin.id to Holding(allocation, FOUNDER_ALLOCATION_USD)),
            granted = state.granted + FOUNDER_ALLOCATION_USD,
        )
        return next to ListingResult.Listed(coin)
    }

    /** "MB-00042". */
    fun certificateNumber(serial: Int): String = "MB-" + serial.toString().padStart(5, '0')

    /** Coins out for [usdNet] dollars in, against a pool of depth [liquidity] at [price]. */
    fun coinsForUsd(price: Double, liquidity: Double, usdNet: Double): Double =
        if (liquidity <= 0.0) usdNet / price else (liquidity / price) * usdNet / (liquidity + usdNet)

    /** Dollars out for selling [coins], before the fee. */
    fun usdForCoins(price: Double, liquidity: Double, coins: Double): Double =
        if (liquidity <= 0.0) coins * price else liquidity * coins / (liquidity / price + coins)

    /** The price after spending [usdNet] on a thin market. */
    fun priceAfterBuy(price: Double, liquidity: Double, usdNet: Double): Double =
        if (liquidity <= 0.0) price else price * (1.0 + usdNet / liquidity).pow(2)

    /** The price after selling [coins] on a thin market. */
    fun priceAfterSell(price: Double, liquidity: Double, coins: Double): Double {
        if (liquidity <= 0.0) return price
        val reserve = liquidity / price
        val out = liquidity * coins / (reserve + coins)
        return (liquidity - out) / (reserve + coins)
    }
}
