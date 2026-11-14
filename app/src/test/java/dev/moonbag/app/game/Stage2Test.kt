package dev.moonbag.app.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage2Test {

    private fun game(cash: Double = 0.0) = GameState.new(1_000L, Random(1)).copy(cash = cash, activeEvent = null)

    private fun withBtc(amount: Double, staked: Double = 0.0) =
        game().copy(holdings = mapOf("btc" to Holding(amount, amount * 1000, staked)))

    @Test
    fun stakeLocksCoinsAndBlocksSelling() {
        val s = Staking.stake(withBtc(2.0), "btc", 1.5)!!
        assertEquals(0.5, s.holdings["btc"]!!.liquid, 1e-9)
        val (_, r) = Trading.sell(s, "btc", 1.0, 0L)
        assertTrue(r is TradeResult.Failed)
        val (_, ok) = Trading.sell(s, "btc", 0.5, 0L)
        assertTrue(ok is TradeResult.Ok)
    }

    @Test
    fun stakeAndUnstakeRejectBadAmounts() {
        val s = withBtc(1.0)
        assertNull(Staking.stake(s, "btc", 2.0))
        assertNull(Staking.stake(s, "btc", 0.0))
        assertNull(Staking.unstake(s, "btc", 0.1))
        val staked = Staking.stake(s, "btc", 1.0)!!
        assertNull(Staking.stake(staked, "btc", 0.1))
        assertEquals(0.0, Staking.unstake(staked, "btc", 1.0)!!.holdings["btc"]!!.staked, 1e-12)
    }

    @Test
    fun stakingPaysMoreForRiskierCoinsAndCompounds() {
        val s = Staking.accrue(withBtc(1.0, 1.0), 3600.0)
        val h = s.holdings["btc"]!!
        assertEquals(1.0 + 0.004, h.amount, 1e-3)
        assertEquals(h.amount, h.staked, 1e-12)
        assertTrue(s.stakingEarned > 0)
        assertTrue(Staking.ratePerHour(Coins.get("woof")!!) > Staking.ratePerHour(Coins.get("btc")!!))
        val a = Staking.accrue(withBtc(1.0, 1.0), 7200.0).holdings["btc"]!!.amount
        val b = Staking.accrue(Staking.accrue(withBtc(1.0, 1.0), 3600.0), 3600.0).holdings["btc"]!!.amount
        assertEquals(a, b, 1e-9)
    }

    @Test
    fun unstakedCoinsEarnNothing() {
        val s = Staking.accrue(withBtc(1.0), 3600.0)
        assertEquals(1.0, s.holdings["btc"]!!.amount, 0.0)
    }

    @Test
    fun farmsRaiseIncomeAndCostGrows() {
        val base = Upgrades()
        assertEquals(0.0, Clicker.perSecond(base), 0.0)
        val s = game(1_000.0)
        val one = Clicker.buyFarm(s, 0)!!
        assertEquals(1.0, Clicker.perSecond(one.upgrades), 1e-9)
        assertEquals(850.0, one.cash, 1e-6)
        assertTrue(Clicker.farmCost(0, 1) > Clicker.farmCost(0, 0))
        assertNull(Clicker.buyFarm(game(10.0), 0))
        assertNull(Clicker.buyFarm(s, 99))
    }

    @Test
    fun flashCrashHitsOnlyTheFirstStep() {
        val start = game().copy(activeEvent = GlobalEvents.start(GlobalEventKind.FLASH_CRASH))
        val after = PriceEngine.advance(start, 1.0, 0L, Random(3), recordEvents = false)
        val down = Coins.all.count { after.price(it.id) < start.price(it.id) }
        assertTrue(down >= Coins.all.size - 2)
        assertFalse(after.activeEvent!!.justStarted)
    }

    @Test
    fun eventsEndAndNewOnesCanStart() {
        var s = game().copy(activeEvent = GlobalEvent(GlobalEventKind.BULL_RUN, 2.0, 240.0))
        val rng = Random(5)
        s = PriceEngine.advance(s, 3.0, 0L, rng, recordEvents = false)
        assertTrue(s.activeEvent == null || s.activeEvent!!.kind != GlobalEventKind.BULL_RUN || s.activeEvent!!.justStarted)
        var started = 0
        repeat(20_000) {
            s = PriceEngine.advance(s, 1.0, 0L, rng, recordEvents = false)
            if (s.activeEvent?.justStarted == true) started++
        }
        assertTrue(started > 3)
    }

    @Test
    fun mintingChargesAndRarityWeightsAddUp() {
        val (s, r) = Nfts.mint(game(100.0), "pixel", 5L, Random(2))
        assertTrue(r is MintResult.Minted)
        assertEquals(50.0, s.cash, 1e-9)
        assertEquals(1, s.nfts.size)
        val (_, broke) = Nfts.mint(game(10.0), "pixel", 5L, Random(2))
        assertTrue(broke is MintResult.NotEnoughCash)
        assertEquals(100, Rarity.entries.sumOf { it.weight })
    }

    @Test
    fun mintLimitIsEnforced() {
        var s = game(1e9)
        val rng = Random(4)
        repeat(Nfts.MAX_OWNED) { s = Nfts.mint(s, "pixel", 0L, rng).first }
        assertEquals(Nfts.MAX_OWNED, s.nfts.size)
        assertTrue(Nfts.mint(s, "pixel", 0L, rng).second is MintResult.LimitReached)
    }

    @Test
    fun mintingIsNotFreeMoneyOnAverage() {
        val rng = Random(11)
        for (col in Nfts.collections) {
            var total = 0.0
            val n = 20_000
            repeat(n) { total += Nfts.rollRarity(rng).multiplier }
            val net = total / n * (1 - Nfts.SALE_FEE)
            assertTrue("${col.id} $net", net < 1.0 + 0.45 && net > 0.5)
        }
    }

    @Test
    fun sellingPaysFloorMinusFeeAndRemovesPiece() {
        val (minted, _) = Nfts.mint(game(100.0), "pixel", 0L, Random(2))
        val nft = minted.nfts.first()
        val expected = Nfts.value(minted, nft) * (1 - Nfts.SALE_FEE)
        val (sold, proceeds) = Nfts.sell(minted, nft.id)
        assertEquals(expected, proceeds!!, 1e-9)
        assertTrue(sold.nfts.isEmpty())
        assertNull(Nfts.sell(sold, nft.id).second)
    }

    @Test
    fun nftValueCountsTowardNetWorth() {
        val (s, _) = Nfts.mint(game(100.0), "pixel", 0L, Random(2))
        assertEquals(s.cash + s.nftValue(), s.netWorth(), 1e-9)
        assertTrue(s.nftValue() > 0)
    }

    @Test
    fun floorsStayInsideTheirBand() {
        var s = game()
        val rng = Random(8)
        repeat(20_000) { s = Nfts.advanceFloors(s, 1.0, 0.0, rng) }
        for (c in Nfts.collections) {
            val f = Nfts.floor(s, c.id)
            assertTrue(f > c.mintPrice / 5 && f < c.mintPrice * 5)
            assertTrue(s.nftHistory[c.id]!!.size <= 240)
        }
    }

    @Test
    fun saveRoundTripKeepsStageTwoData() {
        val rng = Random(6)
        var s = withBtc(2.0, 1.0).copy(cash = 500.0)
        s = Clicker.buyFarm(s, 0)!!
        s = Nfts.mint(s, "orbs", 9L, rng).first
        s = s.copy(activeEvent = GlobalEvent(GlobalEventKind.MEME_SEASON, 100.0, 200.0), stakingEarned = 12.5)
        val back = GameJson.fromJson(GameJson.toJson(s))
        assertEquals(1.0, back.holdings["btc"]!!.staked, 1e-12)
        assertEquals(s.upgrades.farms, back.upgrades.farms)
        assertEquals(s.nfts, back.nfts)
        assertEquals(s.nftFloors, back.nftFloors)
        assertEquals(s.nextNftId, back.nextNftId)
        assertEquals(GlobalEventKind.MEME_SEASON, back.activeEvent!!.kind)
        assertEquals(12.5, back.stakingEarned, 1e-12)
        assertNotNull(back.nftHistory["pixel"])
    }

    @Test
    fun oldSavesStillLoad() {
        val s = GameJson.fromJson(GameJson.toJson(game()).replace("\"farms\"", "\"farmsX\""))
        assertEquals(Clicker.FARMS.size, s.upgrades.farms.size)
    }
}
