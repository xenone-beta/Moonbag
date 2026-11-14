package dev.moonbag.app.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage3Test {

    private fun game(cash: Double = 0.0) = GameState.new(1_000L, Random(1)).copy(cash = cash, activeEvent = null)

    private fun withBtc(amount: Double) = game().copy(holdings = mapOf("btc" to Holding(amount, amount * 1000)))

    private fun withPrice(s: GameState, id: String, price: Double) = s.copy(prices = s.prices + (id to price))

    private fun placed(r: Pair<GameState, OrderResult>): GameState {
        assertTrue(r.second is OrderResult.Placed)
        return r.first
    }

    // ---- orders ----

    @Test
    fun limitBuyReservesCashAndKeepsNetWorth() {
        val s = game(1_000.0)
        val p = s.price("btc")
        val after = placed(Orders.place(s, "btc", OrderType.LIMIT_BUY, p * 0.9, 400.0, 5L))
        assertEquals(600.0, after.cash, 1e-9)
        assertEquals(400.0, after.reservedCash(), 1e-9)
        assertEquals(s.netWorth(), after.netWorth(), 1e-9)
    }

    @Test
    fun orderPlacementRejectsWrongSideOfThePrice() {
        val s = withBtc(1.0).copy(cash = 1_000.0)
        val p = s.price("btc")
        assertTrue(Orders.place(s, "btc", OrderType.LIMIT_BUY, p * 1.1, 100.0, 0L).second is OrderResult.Failed)
        assertTrue(Orders.place(s, "btc", OrderType.LIMIT_SELL, p * 0.9, 0.5, 0L).second is OrderResult.Failed)
        assertTrue(Orders.place(s, "btc", OrderType.STOP_LOSS, p * 1.1, 0.5, 0L).second is OrderResult.Failed)
        assertTrue(Orders.place(s, "btc", OrderType.LIMIT_BUY, p * 0.9, 5_000.0, 0L).second is OrderResult.Failed)
        assertTrue(Orders.place(s, "btc", OrderType.LIMIT_SELL, p * 1.1, 5.0, 0L).second is OrderResult.Failed)
        assertTrue(Orders.place(s, "zzz", OrderType.LIMIT_BUY, 1.0, 1.0, 0L).second is OrderResult.Failed)
    }

    @Test
    fun limitBuyFillsWhenPriceFallsAndNotBefore() {
        val s = game(1_000.0)
        val p = s.price("btc")
        val order = placed(Orders.place(s, "btc", OrderType.LIMIT_BUY, p * 0.9, 500.0, 5L))
        val same = Orders.process(withPrice(order, "btc", p * 0.95), 6L)
        assertEquals(1, same.orders.size)
        val filled = Orders.process(withPrice(order, "btc", p * 0.89), 6L)
        assertTrue(filled.orders.isEmpty())
        assertNotNull(filled.holdings["btc"])
        assertEquals(1, filled.orderFills.size)
        assertEquals(0.0, filled.reservedCash(), 0.0)
    }

    @Test
    fun limitBuyCostsTheReservedAmountOnly() {
        val s = game(1_000.0)
        val p = s.price("btc")
        val order = placed(Orders.place(s, "btc", OrderType.LIMIT_BUY, p * 0.9, 500.0, 5L))
        val filled = Orders.process(withPrice(order, "btc", p * 0.89), 6L)
        assertEquals(500.0, filled.cash, 1e-6)
    }

    @Test
    fun limitSellAndStopLossFire() {
        val s = withBtc(2.0)
        val p = s.price("btc")
        val sell = placed(Orders.place(s, "btc", OrderType.LIMIT_SELL, p * 1.2, 1.0, 0L))
        assertEquals(1, Orders.process(withPrice(sell, "btc", p * 1.1), 1L).orders.size)
        val sold = Orders.process(withPrice(sell, "btc", p * 1.25), 1L)
        assertTrue(sold.orders.isEmpty())
        assertEquals(1.0, sold.holdings["btc"]!!.amount, 1e-9)
        assertTrue(sold.cash > 0)

        val stop = placed(Orders.place(s, "btc", OrderType.STOP_LOSS, p * 0.9, 2.0, 0L))
        val stopped = Orders.process(withPrice(stop, "btc", p * 0.85), 1L)
        assertNull(stopped.holdings["btc"])
        assertTrue(stopped.orders.isEmpty())
    }

    @Test
    fun sellOrdersOnlyUseFreeCoinsAndDropWhenNoneLeft() {
        val s = withBtc(2.0)
        val p = s.price("btc")
        val order = placed(Orders.place(s, "btc", OrderType.LIMIT_SELL, p * 1.2, 2.0, 0L))
        val staked = Staking.stake(order, "btc", 2.0)!!
        val after = Orders.process(withPrice(staked, "btc", p * 1.3), 1L)
        assertTrue(after.orders.isEmpty())
        assertEquals(2.0, after.holdings["btc"]!!.amount, 1e-9)
        assertEquals(0.0, after.cash, 0.0)
    }

    @Test
    fun cancelRefundsAReservedBuy() {
        val s = game(1_000.0)
        val order = placed(Orders.place(s, "btc", OrderType.LIMIT_BUY, s.price("btc") * 0.9, 300.0, 0L))
        val back = Orders.cancel(order, order.orders.first().id)
        assertEquals(1_000.0, back.cash, 1e-9)
        assertTrue(back.orders.isEmpty())
    }

    @Test
    fun ordersFillDuringOfflineCatchUp() {
        var s = game(100_000.0)
        val p = s.price("woof")
        s = placed(Orders.place(s, "woof", OrderType.LIMIT_BUY, p * 0.999, 1_000.0, 0L))
        val rng = Random(2)
        s = PriceEngine.catchUp(s, 6 * 3600L, 10L, rng)
        assertTrue(s.orders.isEmpty() || s.orderFills.isNotEmpty() || s.price("woof") > p * 0.999)
    }

    @Test
    fun orderLimitIsEnforced() {
        var s = game(1_000_000.0)
        val p = s.price("btc")
        repeat(Orders.MAX_OPEN) { s = placed(Orders.place(s, "btc", OrderType.LIMIT_BUY, p * 0.5, 10.0, 0L)) }
        assertTrue(Orders.place(s, "btc", OrderType.LIMIT_BUY, p * 0.5, 10.0, 0L).second is OrderResult.Failed)
    }

    // ---- coin management ----

    private fun withOwnCoin(): GameState {
        val (s, r) = Listing.create(game(50_000.0), "Test Coin", "TST", "star", 0xFF123456, CoinStyle.MEME, 0L)
        assertTrue(r is ListingResult.Listed)
        return s
    }

    @Test
    fun marketingCostsMoneyAndOnlyOneAtATime() {
        val s = withOwnCoin()
        val id = s.customCoins.first().id
        val a = CoinManagement.startMarketing(s, id, MarketingTier.SMALL)!!
        assertEquals(s.cash - 500.0, a.cash, 1e-9)
        assertTrue(CoinManagement.marketingActive(a, id))
        assertNull(CoinManagement.startMarketing(a, id, MarketingTier.SMALL))
        assertNull(CoinManagement.startMarketing(s, "btc", MarketingTier.SMALL))
        assertNull(CoinManagement.startMarketing(s.copy(cash = 10.0), id, MarketingTier.SMALL))
    }

    @Test
    fun marketingPushesThePriceUp() {
        val s = withOwnCoin()
        val id = s.customCoins.first().id
        var with = CoinManagement.startMarketing(s, id, MarketingTier.BIG)!!
        var without = s
        repeat(80) {
            with = PriceEngine.advance(with, 1.0, 0L, Random(it), recordEvents = false)
            without = PriceEngine.advance(without, 1.0, 0L, Random(it), recordEvents = false)
        }
        assertTrue(with.price(id) > without.price(id))
    }

    @Test
    fun campaignsEndAndAreRemoved() {
        val s = withOwnCoin()
        val id = s.customCoins.first().id
        var c = CoinManagement.startMarketing(s, id, MarketingTier.SMALL)!!
        repeat(5) { c = PriceEngine.advance(c, 40.0, 0L, Random(it), recordEvents = false) }
        assertTrue(c.campaigns.isEmpty())
    }

    @Test
    fun longOfflineStepDoesNotOvershootACampaign() {
        val c = listOf(Campaign("c1", CampaignKind.MARKETING, 5.0, 100.0, 200.0))
        assertEquals(500.0, CoinManagement.push(c, "c1", 1_000.0), 1e-9)
        assertEquals(0.0, CoinManagement.push(c, "other", 10.0), 0.0)
    }

    @Test
    fun burnRemovesCoinsLowersSupplyAndBooksTheLoss() {
        val s = withOwnCoin()
        val id = s.customCoins.first().id
        val held = s.holdings[id]!!
        val b = CoinManagement.burn(s, id, held.amount / 2)!!
        assertEquals(held.amount / 2, b.holdings[id]!!.amount, 1e-6)
        assertEquals(held.amount / 2, b.customCoins.first().burned, 1e-6)
        assertTrue(b.coin(id)!!.supply < s.coin(id)!!.supply)
        assertTrue(b.realizedPnl < 0)
        assertTrue(b.campaigns.any { it.kind == CampaignKind.BURN })
        assertNull(CoinManagement.burn(s, id, held.amount * 2))
        assertNull(CoinManagement.burn(s, "btc", 1.0))
        assertNull(CoinManagement.burn(Staking.stake(s, id, held.amount)!!, id, 1.0))
    }

    @Test
    fun burningEverythingClosesThePosition() {
        val s = withOwnCoin()
        val id = s.customCoins.first().id
        val b = CoinManagement.burn(s, id, s.holdings[id]!!.amount)!!
        assertNull(b.holdings[id])
    }

    // ---- rivals ----

    @Test
    fun rivalWorthIsDeterministicAndPositive() {
        for (r in Rivals.all) {
            for (d in listOf(0.0, 0.5, 3.0, 40.0, 400.0)) {
                val w = Rivals.netWorth(r, d)
                assertTrue("${r.id} $d $w", w.isFinite() && w >= 5.0)
                assertEquals(w, Rivals.netWorth(r, d), 0.0)
            }
        }
    }

    @Test
    fun boardIsSortedAndContainsYouOnce() {
        val s = game(500.0)
        val board = Rivals.board(s, s.startedAt + 86_400_000L, "You")
        assertEquals(Rivals.all.size + 1, board.size)
        assertEquals(1, board.count { it.isPlayer })
        assertEquals(board.map { it.netWorth }.sortedDescending(), board.map { it.netWorth })
        assertTrue(Rivals.rank(s, s.startedAt) in 1..board.size)
    }

    @Test
    fun beingRicherImprovesYourRank() {
        val poor = game(0.0)
        val rich = game(10_000_000.0)
        val now = poor.startedAt + 86_400_000L
        assertTrue(Rivals.rank(rich, now) < Rivals.rank(poor, now))
        assertEquals(1, Rivals.rank(rich, now))
    }

    // ---- saving ----

    @Test
    fun saveRoundTripKeepsOrdersCampaignsAndStart() {
        var s = withOwnCoin()
        val id = s.customCoins.first().id
        s = placed(Orders.place(s, "btc", OrderType.LIMIT_BUY, s.price("btc") * 0.9, 100.0, 4L))
        s = CoinManagement.startMarketing(s, id, MarketingTier.MEDIUM)!!
        s = CoinManagement.burn(s, id, 10.0)!!
        s = s.copy(orderFills = listOf(OrderFill(3L, "btc", OrderType.STOP_LOSS, 1.5, 2.0)))
        val back = GameJson.fromJson(GameJson.toJson(s))
        assertEquals(s.orders, back.orders)
        assertEquals(s.nextOrderId, back.nextOrderId)
        assertEquals(s.orderFills, back.orderFills)
        assertEquals(s.campaigns, back.campaigns)
        assertEquals(s.startedAt, back.startedAt)
        assertEquals(10.0, back.customCoins.first().burned, 1e-9)
        assertEquals(s.netWorth(), back.netWorth(), 1e-6)
    }

    @Test
    fun oldSavesGetAStartDate() {
        val text = GameJson.toJson(game()).replace("\"startedAt\"", "\"startedAtX\"")
        assertTrue(GameJson.fromJson(text).startedAt > 0)
        assertFalse(GameJson.fromJson(text).orders.isNotEmpty())
    }
}
