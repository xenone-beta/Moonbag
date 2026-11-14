package dev.moonbag.app.game

data class Holding(val amount: Double, val costBasis: Double, val staked: Double = 0.0) {
    /** Average price paid per coin, fees included. */
    val averagePrice: Double get() = if (amount > 0) costBasis / amount else 0.0

    /** Coins that are not locked in staking and can be sold. */
    val liquid: Double get() = (amount - staked).coerceAtLeast(0.0)
}

enum class TradeSide { BUY, SELL }

data class Trade(
    val time: Long,
    val coinId: String,
    val side: TradeSide,
    val amount: Double,
    val price: Double,
    /** Money paid (buy) or received after the fee (sell). */
    val usd: Double,
    val fee: Double,
    /** Profit of this sale; 0 for buys. */
    val pnl: Double,
)

enum class EventKind { PUMP, DUMP, RUG }

data class MarketEvent(val time: Long, val coinId: String, val kind: EventKind, val percent: Double)

/** Levels of the four clicker upgrades. */
data class Upgrades(
    val tap: Int = 0,
    val auto: Int = 0,
    val crit: Int = 0,
    val vault: Int = 0,
    /** How many of each mining farm are owned, in the order of [Clicker.FARMS]. */
    val farms: List<Int> = List(Clicker.FARMS.size) { 0 },
)

data class GameState(
    val cash: Double = 0.0,
    val holdings: Map<String, Holding> = emptyMap(),
    val prices: Map<String, Double>,
    /** Recent prices per coin, oldest first. */
    val history: Map<String, List<Double>>,
    /** Recent net worth (cash plus coins), oldest first. */
    val netHistory: List<Double> = emptyList(),
    /** Market mood: positive lifts all coins, negative drags them down. Roughly -3..3. */
    val sentiment: Double = 0.0,
    val upgrades: Upgrades = Upgrades(),
    /** Everything earned by tapping and mining. This is the "invested" total used for profit. */
    val lifetimeEarned: Double = 0.0,
    val taps: Long = 0,
    val realizedPnl: Double = 0.0,
    val feesPaid: Double = 0.0,
    val trades: List<Trade> = emptyList(),
    val events: List<MarketEvent> = emptyList(),
    val savedAt: Long = 0L,
    /** Coins you created. */
    val customCoins: List<CustomCoin> = emptyList(),
    /** How many coins were ever listed; used for certificate numbers. */
    val coinsListed: Int = 0,
    /** Free coins received (founder allocations). Not counted as profit. */
    val granted: Double = 0.0,
    /** Value of everything earned by staking, at the prices of the moment it was earned. */
    val stakingEarned: Double = 0.0,
    val activeEvent: GlobalEvent? = null,
    val nfts: List<Nft> = emptyList(),
    /** Floor price per NFT collection. */
    val nftFloors: Map<String, Double> = emptyMap(),
    val nftHistory: Map<String, List<Double>> = emptyMap(),
    val nextNftId: Long = 1L,
) {
    /** Built-in coins followed by the ones you created. */
    val allCoins: List<CoinDef> get() = Coins.all + customCoins.map { it.toDef() }

    fun coin(id: String): CoinDef? = Coins.get(id) ?: customCoins.firstOrNull { it.id == id }?.toDef()

    fun price(coinId: String): Double = prices[coinId] ?: coin(coinId)?.startPrice ?: 0.0

    fun holdingsValue(): Double = holdings.entries.sumOf { (id, h) -> h.amount * price(id) }

    fun nftValue(): Double = nfts.sumOf { Nfts.value(this, it) }

    fun netWorth(): Double = cash + holdingsValue() + nftValue()

    /** Profit or loss against everything ever earned by clicking. */
    fun totalProfit(): Double = netWorth() - lifetimeEarned - granted

    /** Price change over the stored history, as a fraction (0.05 = +5 percent). */
    fun change(coinId: String): Double {
        val list = history[coinId].orEmpty()
        val first = list.firstOrNull() ?: return 0.0
        return if (first > 0) price(coinId) / first - 1.0 else 0.0
    }

    companion object {
        const val STARTING_CASH = 0.0

        /** A fresh game. The market is pre-run for a while so the charts are not empty. */
        fun new(now: Long, random: kotlin.random.Random = kotlin.random.Random.Default): GameState {
            var state = GameState(
                cash = STARTING_CASH,
                prices = Coins.all.associate { it.id to it.startPrice },
                history = Coins.all.associate { it.id to listOf(it.startPrice) },
                netHistory = listOf(STARTING_CASH),
                savedAt = now,
                nftFloors = Nfts.collections.associate { it.id to it.mintPrice },
                nftHistory = Nfts.collections.associate { it.id to listOf(it.mintPrice) },
            )
            repeat(120) { state = PriceEngine.advance(state, 1.0, now, random, recordEvents = false) }
            // Drop the pre-run's bookkeeping: the game starts from what the charts show now.
            return state.copy(events = emptyList(), activeEvent = null, netHistory = List(state.netHistory.size) { STARTING_CASH })
        }
    }
}
