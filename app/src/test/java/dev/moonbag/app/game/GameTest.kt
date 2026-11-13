package dev.moonbag.app.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {

    private fun newGame(seed: Int = 1) = GameState.new(1_000L, Random(seed))

    @Test
    fun newGameHasChartsAndNoMoney() {
        val s = newGame()
        assertEquals(0.0, s.cash, 0.0)
        assertEquals(Coins.all.size, s.prices.size)
        assertTrue(s.history.values.all { it.size > 50 })
        assertTrue(s.prices.values.all { it.isFinite() && it > 0 })
    }

    @Test
    fun sameSeedGivesSameMarket() {
        val a = PriceEngine.advance(newGame(5), 1.0, 0L, Random(9))
        val b = PriceEngine.advance(newGame(5), 1.0, 0L, Random(9))
        assertEquals(a.prices, b.prices)
    }

    @Test
    fun longRunStaysFiniteAndBounded() {
        var s = newGame(3)
        val rng = Random(7)
        repeat(20_000) { s = PriceEngine.advance(s, 1.0, 0L, rng) }
        for (coin in Coins.all) {
            val p = s.price(coin.id)
            assertTrue("${coin.id} $p", p.isFinite() && p > 0)
            assertTrue(p < coin.startPrice * 60 && p > coin.startPrice / 200)
        }
        assertTrue(s.history.values.all { it.size <= PriceEngine.MAX_POINTS })
        assertTrue(s.netHistory.size <= PriceEngine.MAX_POINTS)
    }

    @Test
    fun catchUpSimulatesOfflineTime() {
        val s = newGame(2)
        val later = PriceEngine.catchUp(s, 6 * 3600L, 0L, Random(4))
        assertTrue(later.history.getValue("woof").size > s.history.getValue("woof").size)
        assertTrue(later.prices.values.all { it.isFinite() && it > 0 })
        assertEquals(s, PriceEngine.catchUp(s, 1L, 0L, Random(4)))
    }

    @Test
    fun buyThenSellRoundTripLosesOnlyFees() {
        val s0 = newGame().copy(cash = 100.0)
        val (s1, r1) = Trading.buy(s0, "giga", 100.0, 0L)
        assertTrue(r1 is TradeResult.Ok)
        assertEquals(0.0, s1.cash, 1e-9)
        val held = s1.holdings.getValue("giga")
        val (s2, r2) = Trading.sell(s1, "giga", held.amount, 0L)
        assertTrue(r2 is TradeResult.Ok)
        assertTrue(s2.holdings.isEmpty())
        val expected = 100.0 * (1 - Trading.FEE_RATE) * (1 - Trading.FEE_RATE)
        assertEquals(expected, s2.cash, 1e-6)
        assertEquals(s2.cash - 100.0, s2.realizedPnl, 1e-6)
    }

    @Test
    fun partialSellKeepsProportionalBasis() {
        val s0 = newGame().copy(cash = 200.0)
        val (s1, _) = Trading.buy(s0, "bnna", 200.0, 0L)
        val h = s1.holdings.getValue("bnna")
        val (s2, _) = Trading.sell(s1, "bnna", h.amount / 4, 0L)
        val rest = s2.holdings.getValue("bnna")
        assertEquals(h.amount * 0.75, rest.amount, 1e-9)
        assertEquals(h.costBasis * 0.75, rest.costBasis, 1e-9)
    }

    @Test
    fun invalidOrdersAreRejected() {
        val s = newGame().copy(cash = 5.0)
        assertEquals(TradeError.NOT_ENOUGH_CASH, (Trading.buy(s, "giga", 6.0, 0L).second as TradeResult.Failed).error)
        assertEquals(TradeError.TOO_SMALL, (Trading.buy(s, "giga", 0.0, 0L).second as TradeResult.Failed).error)
        assertEquals(TradeError.UNKNOWN_COIN, (Trading.buy(s, "nope", 1.0, 0L).second as TradeResult.Failed).error)
        assertEquals(TradeError.NOT_ENOUGH_COINS, (Trading.sell(s, "giga", 1.0, 0L).second as TradeResult.Failed).error)
        assertEquals(s, Trading.buy(s, "giga", 6.0, 0L).first)
    }

    @Test
    fun tapEarnsAndCountsAsInvested() {
        val s = newGame()
        val (n, r) = Clicker.tap(s, Random(1))
        assertEquals(s.cash + r.value, n.cash, 1e-9)
        assertEquals(r.value, n.lifetimeEarned, 1e-9)
        assertEquals(1L, n.taps)
        assertEquals(0.0, n.totalProfit(), 1e-9)
    }

    @Test
    fun upgradesCostMoreAndCanBeMaxed() {
        for (kind in UpgradeKind.values()) {
            assertTrue(Clicker.cost(kind, 5) > Clicker.cost(kind, 4))
        }
        assertNull(Clicker.buy(newGame().copy(cash = 5.0), UpgradeKind.TAP))
        val rich = newGame().copy(cash = 1e9, upgrades = Upgrades(vault = Clicker.MAX_VAULT_LEVEL))
        assertNull(Clicker.buy(rich, UpgradeKind.VAULT))
        val bought = Clicker.buy(rich, UpgradeKind.TAP)
        assertNotNull(bought)
        assertEquals(1, bought!!.upgrades.tap)
        assertTrue(Clicker.tapValue(bought.upgrades) > Clicker.tapValue(rich.upgrades))
    }

    @Test
    fun offlineMiningIsCappedByTheVault() {
        val base = newGame().copy(upgrades = Upgrades(auto = 4), savedAt = 1_000L)
        val engine = GameEngine(base, Random(1)) { 1_000L + 100L * 3600 * 1000 }
        val report = engine.catchUp()
        assertNotNull(report)
        val expected = Clicker.perSecond(base.upgrades) * Clicker.vaultHours(base.upgrades) * 3600
        assertEquals(expected, report!!.earned, 1e-6)
        assertEquals(expected, engine.state.value.cash, 1e-6)
        assertNull(engine.catchUp())
    }

    @Test
    fun saveRoundTrip() {
        var s = newGame(8).copy(cash = 50.0, upgrades = Upgrades(1, 2, 3, 4), savedAt = 42L)
        s = Trading.buy(s, "capy", 20.0, 7L).first
        s = PriceEngine.catchUp(s, 3600, 9L, Random(3))
        val back = GameJson.fromJson(GameJson.toJson(s))
        assertEquals(s.cash, back.cash, 1e-9)
        assertEquals(s.holdings, back.holdings)
        assertEquals(s.upgrades, back.upgrades)
        assertEquals(s.prices, back.prices)
        assertEquals(s.history, back.history)
        assertEquals(s.trades, back.trades)
        assertEquals(s.events, back.events)
        assertEquals(42L, back.savedAt)
    }

    @Test
    fun brokenSaveIsHandled() {
        assertFalse(runCatching { GameJson.fromJson("{}") }.isFailure)
        assertTrue(runCatching { GameJson.fromJson("not json") }.isFailure)
        val minimal = GameJson.fromJson("""{"cash":-5,"prices":{"giga":"x"}}""")
        assertEquals(0.0, minimal.cash, 0.0)
        assertEquals(Coins.all.size, minimal.prices.size)
    }
}
