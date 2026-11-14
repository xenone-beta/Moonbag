package dev.moonbag.app.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingTest {

    private fun rich(cash: Double = 5_000.0) = GameState.new(1_000L, Random(1)).copy(cash = cash)

    private fun list(s: GameState, name: String = "Moon Cat", ticker: String = "MCAT", style: CoinStyle = CoinStyle.MEME) =
        Listing.create(s, name, ticker, "paw", 0xFF7C4DFF, style, 5_000L)

    @Test
    fun builtInCoinsIncludeMajors() {
        assertEquals(10, Coins.majors.size)
        assertTrue(Coins.get("btc")!!.startPrice > 1000)
        assertEquals(Coins.majors.size + Coins.memes.size, Coins.all.size)
        assertEquals(Coins.all.size, Coins.all.map { it.id }.toSet().size)
        assertEquals(Coins.all.size, Coins.all.map { it.symbol }.toSet().size)
    }

    @Test
    fun listingCostsTheFeeAndGivesACertificateAndAllocation() {
        val before = rich()
        val (after, result) = list(before)
        val coin = (result as ListingResult.Listed).coin
        assertEquals(before.cash - Listing.FEE, after.cash, 1e-9)
        assertEquals(1, coin.serial)
        assertEquals("MB-00001", Listing.certificateNumber(coin.serial))
        assertEquals(Listing.FOUNDER_ALLOCATION_USD, after.holdings.getValue(coin.id).amount * after.price(coin.id), 1e-6)
        assertEquals(before.totalProfit() - Listing.FEE, after.totalProfit(), 1e-6)
        assertEquals(coin.toDef(), after.coin(coin.id))
        assertTrue(after.allCoins.any { it.id == coin.id })
    }

    @Test
    fun listingValidation() {
        fun err(r: Pair<GameState, ListingResult>) = (r.second as ListingResult.Failed).error
        assertEquals(ListingError.NOT_ENOUGH_CASH, err(list(rich(1_999.0))))
        assertEquals(ListingError.BAD_NAME, err(list(rich(), name = "A")))
        assertEquals(ListingError.BAD_TICKER, err(list(rich(), ticker = "x")))
        assertEquals(ListingError.BAD_TICKER, err(list(rich(), ticker = "123")))
        assertEquals(ListingError.TICKER_TAKEN, err(list(rich(), ticker = "btc")))
        var s = rich(100_000.0)
        repeat(Listing.MAX_COINS) { i -> s = list(s, ticker = "T$i" + "X").first }
        assertEquals(Listing.MAX_COINS, s.customCoins.size)
        assertEquals(ListingError.LIMIT_REACHED, err(list(s, ticker = "OVER")))
        assertEquals(ListingError.TICKER_TAKEN, err(list(rich().let { list(it).first }, ticker = "mcat")))
    }

    @Test
    fun cleanTickerKeepsLettersAndDigits() {
        assertEquals("AB12", Listing.cleanTicker(" a-b 1_2 "))
        assertEquals(Listing.MAX_TICKER, Listing.cleanTicker("abcdefghij").length)
    }

    @Test
    fun customCoinMovesWithYourOrdersButRoundTripLoses() {
        val (s0, r) = list(rich(10_000.0), style = CoinStyle.MOON)
        val id = (r as ListingResult.Listed).coin.id
        val p0 = s0.price(id)
        val (s1, _) = Trading.buy(s0, id, 1_000.0, 0L)
        assertTrue(s1.price(id) > p0 * 1.2)
        // sell back only the coins that were just bought, not the founder allocation
        val bought = s1.holdings.getValue(id).amount - s0.holdings.getValue(id).amount
        val (s2, _) = Trading.sell(s1, id, bought, 0L)
        assertTrue(s2.cash > s1.cash)
        assertTrue(s2.cash < s1.cash + 1_000.0 * 0.995)
        assertTrue(s2.price(id) < s1.price(id))
    }

    @Test
    fun builtInCoinsIgnoreYourOrders() {
        val s = rich()
        val (n, _) = Trading.buy(s, "btc", 1_000.0, 0L)
        assertEquals(s.price("btc"), n.price("btc"), 0.0)
    }

    @Test
    fun customCoinSurvivesSaveAndSimulation() {
        val (s, r) = list(rich())
        val coin = (r as ListingResult.Listed).coin
        val simulated = PriceEngine.catchUp(s, 3600, 9L, Random(2))
        val back = GameJson.fromJson(GameJson.toJson(simulated))
        assertEquals(listOf(coin), back.customCoins)
        assertEquals(1, back.coinsListed)
        assertEquals(simulated.granted, back.granted, 1e-9)
        assertEquals(simulated.prices, back.prices)
        assertEquals(simulated.holdings, back.holdings)
        assertTrue(back.price(coin.id) > 0 && back.price(coin.id).isFinite())
    }

    @Test
    fun poolMathIsConsistent() {
        val price = 0.01
        val liq = 20_000.0
        val coins = Listing.coinsForUsd(price, liq, 1_000.0)
        assertTrue(coins < 1_000.0 / price)
        assertEquals(1_000.0 / price, Listing.coinsForUsd(price, 0.0, 1_000.0), 1e-9)
        assertTrue(Listing.priceAfterBuy(price, liq, 1_000.0) > price)
        assertTrue(Listing.priceAfterSell(price, liq, coins) < price)
        assertEquals(price, Listing.priceAfterBuy(price, 0.0, 1_000.0), 0.0)
    }
}
