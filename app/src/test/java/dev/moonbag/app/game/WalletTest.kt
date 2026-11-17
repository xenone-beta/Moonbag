package dev.moonbag.app.game

import java.util.Random
import kotlin.random.Random as KRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletTest {

    private val words = Wallet.generate(Random(7))
    private val me = Wallet.addresses(words).values.map { Wallet.canonical(it) }.toSet()

    private fun game(cash: Double = 10.0) = GameState.new(1_000L, KRandom(1)).copy(cash = cash, activeEvent = null)

    private fun withBtc(amount: Double, cash: Double = 10.0) =
        game(cash).copy(holdings = mapOf("btc" to Holding(amount, amount * 1000)))

    private fun other(chain: Chain) = Wallet.rivalAddress("nina", chain)

    // ---- words and addresses ----

    @Test fun wordListHas256DifferentWords() {
        assertEquals(256, WordList.words.size)
        assertEquals(256, WordList.set.size)
        assertTrue(WordList.words.all { it == it.lowercase() && it.all { c -> c in 'a'..'z' } })
    }

    @Test fun generatesTwelveValidWords() {
        assertEquals(12, words.size)
        assertTrue(Wallet.isValid(words))
        assertEquals(words, Wallet.generate(Random(7)))
        assertNotEquals(words, Wallet.generate(Random(8)))
    }

    @Test fun validationRejectsWrongInput() {
        assertFalse(Wallet.isValid(words.drop(1)))
        assertFalse(Wallet.isValid(words.dropLast(1) + "notaword"))
        assertEquals(words, Wallet.normalize(words.joinToString(" ").uppercase().replace(" ", ",  \n")))
    }

    @Test fun everyChainGetsAValidAddressThatIsStable() {
        for (chain in Chains.all) {
            val a = Wallet.address(words, chain)
            assertTrue("${chain.id}: $a", Wallet.isAddress(chain, a))
            assertEquals(a, Wallet.address(words, chain))
        }
        assertEquals(Chains.all.size, Wallet.addresses(words).values.toSet().size)
        assertNotEquals(Wallet.address(words, Chains.all[0]), Wallet.address(Wallet.generate(Random(9)), Chains.all[0]))
    }

    @Test fun addressFormats() {
        val moon = Chains.get("moon")!!
        val orbit = Chains.get("orbit")!!
        val nova = Chains.get("nova")!!
        assertTrue(Wallet.address(words, moon).startsWith("0x") && Wallet.address(words, moon).length == 42)
        assertTrue(Wallet.address(words, nova).startsWith("nova1") && Wallet.address(words, nova).length == 43)
        assertTrue(Wallet.address(words, orbit).length in 32..44)
        assertFalse(Wallet.isAddress(moon, "0x123"))
        assertFalse(Wallet.isAddress(moon, Wallet.address(words, nova)))
        assertFalse(Wallet.isAddress(nova, "nova1" + "b".repeat(38)))
        assertTrue(Wallet.isAddress(moon, "  " + Wallet.address(words, moon).uppercase().replace("0X", "0x") + " "))
    }

    @Test fun shortensLongAddresses() {
        assertEquals("0x1234…cdef", Wallet.shorten("0x1234" + "a".repeat(30) + "cdef"))
        assertEquals("short", Wallet.shorten("short"))
    }

    @Test fun networksAreSpreadAndCustomCoinsLiveOnMoonchain() {
        assertTrue(Coins.all.map { Chains.of(it).id }.toSet().size >= 3)
        assertEquals("moon", Chains.of(Coins.all[0].copy(kind = CoinKind.CUSTOM)).id)
        assertEquals(Chains.of(Coins.all[0]), Chains.of(Coins.all[0]))
    }

    // ---- send ----

    @Test fun sendMovesCoinsChargesTheFeeAndKeepsProfit() {
        val s = withBtc(2.0)
        val chain = Chains.of(Coins.get("btc")!!)
        val before = s.totalProfit()
        val (next, r) = Transfers.send(s, "btc", 0.5, other(chain), me, 5L)
        assertTrue(r is SendResult.Ok)
        assertEquals(1.5, next.holdings["btc"]!!.amount, 1e-9)
        assertEquals(1500.0, next.holdings["btc"]!!.costBasis, 1e-6)
        assertEquals(10.0 - chain.feeUsd, next.cash, 1e-9)
        assertEquals(before - chain.feeUsd, next.totalProfit(), 1e-6)
        assertEquals(TxKind.SEND, next.walletTxs.single().kind)
        assertEquals(0.5, next.walletTxs.single().amount, 1e-12)
    }

    @Test fun sendingEverythingClosesThePosition() {
        val s = withBtc(1.0)
        val chain = Chains.of(Coins.get("btc")!!)
        val (next, r) = Transfers.send(s, "btc", 1.0, other(chain), me, 5L)
        assertTrue(r is SendResult.Ok)
        assertNull(next.holdings["btc"])
    }

    private fun failure(r: Pair<GameState, SendResult>): SendError = (r.second as SendResult.Failed).error

    @Test fun sendRefusesBadInput() {
        val s = withBtc(1.0)
        val chain = Chains.of(Coins.get("btc")!!)
        val ok = other(chain)
        assertEquals(SendError.BAD_AMOUNT, failure(Transfers.send(s, "btc", 0.0, ok, me, 1L)))
        assertEquals(SendError.BAD_AMOUNT, failure(Transfers.send(s, "btc", Double.NaN, ok, me, 1L)))
        assertEquals(SendError.NOT_ENOUGH_COINS, failure(Transfers.send(s, "btc", 2.0, ok, me, 1L)))
        assertEquals(SendError.NOT_ENOUGH_COINS, failure(Transfers.send(s, "eth", 1.0, ok, me, 1L)))
        assertEquals(SendError.BAD_ADDRESS, failure(Transfers.send(s, "btc", 0.5, "nope", me, 1L)))
        assertEquals(SendError.UNKNOWN_COIN, failure(Transfers.send(s, "zzz", 0.5, ok, me, 1L)))
        val mine = Wallet.address(words, chain)
        assertEquals(SendError.OWN_ADDRESS, failure(Transfers.send(s, "btc", 0.5, mine, me, 1L)))
        if (chain.style == AddressStyle.HEX) {
            assertEquals(SendError.OWN_ADDRESS, failure(Transfers.send(s, "btc", 0.5, mine.uppercase().replace("0X", "0x"), me, 1L)))
        }
        assertEquals(SendError.NOT_ENOUGH_FEE, failure(Transfers.send(withBtc(1.0, cash = 0.0), "btc", 0.5, ok, me, 1L)))
    }

    @Test fun stakedCoinsCannotBeSent() {
        val s = game().copy(holdings = mapOf("btc" to Holding(1.0, 1000.0, staked = 0.8)))
        val chain = Chains.of(Coins.get("btc")!!)
        assertEquals(SendError.NOT_ENOUGH_COINS, failure(Transfers.send(s, "btc", 0.5, other(chain), me, 1L)))
    }

    // ---- airdrop ----

    @Test fun airdropGivesCoinsOnceInTheCooldown() {
        val s = game(0.0)
        val first = Transfers.claimAirdrop(s, 10_000_000L, KRandom(3))!!
        val value = first.second.valueUsd
        assertTrue(value in 5.0..25.0)
        assertEquals(value, first.first.holdingsValue(), 1e-6)
        assertEquals(s.totalProfit(), first.first.totalProfit(), 1e-6)
        assertNull(Transfers.claimAirdrop(first.first, 10_000_000L + 1000, KRandom(4)))
        assertTrue(Transfers.airdropWait(first.first, 10_000_000L + 1000) > 0)
        assertNull(Transfers.claimAirdrop(first.first, 10_000_000L + Transfers.AIRDROP_COOLDOWN_MS - 1, KRandom(4)))
        assertTrue(Transfers.claimAirdrop(first.first, 10_000_000L + Transfers.AIRDROP_COOLDOWN_MS, KRandom(4)) != null)
        assertEquals(TxKind.AIRDROP, first.first.walletTxs.first().kind)
    }

    // ---- swap ----

    @Test fun swapTurnsOneCoinIntoAnotherAndKeepsCash() {
        val s = withBtc(1.0, cash = 5.0)
        val (next, r) = Swaps.swap(s, "btc", "eth", 0.5, 7L)
        assertTrue(r is SwapResult.Ok)
        r as SwapResult.Ok
        assertEquals(0.5, next.holdings["btc"]!!.amount, 1e-9)
        assertEquals(r.tx.toAmount, next.holdings["eth"]!!.amount, 1e-9)
        assertEquals(5.0, next.cash, 1e-6)
        assertEquals(s.trades, next.trades)
        assertEquals(TxKind.SWAP, next.walletTxs.single().kind)
        // Two fees of 0.5 percent: a bit less than the fair amount comes back.
        val fair = 0.5 * s.price("btc") / s.price("eth")
        assertEquals(fair * 0.995 * 0.995, r.tx.toAmount, fair * 1e-6)
    }

    @Test fun swapRefusals() {
        val s = withBtc(1.0)
        assertEquals(SwapError.SAME_COIN, (Swaps.swap(s, "btc", "btc", 0.5, 1L).second as SwapResult.Failed).error)
        val tooMuch = Swaps.swap(s, "btc", "eth", 5.0, 1L).second as SwapResult.Failed
        assertEquals(TradeError.NOT_ENOUGH_COINS, tooMuch.trade)
        assertEquals(s, Swaps.swap(s, "btc", "eth", 5.0, 1L).first)
        assertTrue(Swaps.swap(s, "btc", "nope", 0.5, 1L).second is SwapResult.Failed)
    }

    @Test fun quoteMatchesTheSwapAndShowsTheLoss() {
        val s = withBtc(1.0)
        val q = Swaps.quote(s, "btc", "eth", 0.5)!!
        val real = (Swaps.swap(s, "btc", "eth", 0.5, 1L).second as SwapResult.Ok).tx
        assertEquals(real.toAmount, q.toAmount, 1e-12)
        assertTrue(q.loss in 0.009..0.011)
        assertNull(Swaps.quote(s, "btc", "btc", 0.5))
    }

    // ---- saving ----

    @Test fun walletActivityAndAirdropSurviveSaving() {
        val s = withBtc(1.0)
        val chain = Chains.of(Coins.get("btc")!!)
        val sent = Transfers.send(s, "btc", 0.25, other(chain), me, 5L).first
        val drop = Transfers.claimAirdrop(sent, 50L, KRandom(1))!!.first
        val back = GameJson.fromJson(GameJson.toJson(drop))
        assertEquals(drop.walletTxs, back.walletTxs)
        assertEquals(drop.lastAirdropAt, back.lastAirdropAt)
        assertEquals(drop.granted, back.granted, 1e-9)
    }

    @Test fun contactsRoundTripAndDropBadOnes() {
        val chain = Chains.get("orbit")!!
        val good = Contact("Nina", other(chain), "orbit")
        assertEquals(listOf(good), ContactsJson.fromJson(ContactsJson.toJson(listOf(good))))
        assertTrue(ContactsJson.fromJson("""[{"name":"x","address":"bad","chain":"orbit"},{"name":"","address":"y","chain":"zzz"}]""").isEmpty())
        assertTrue(ContactsJson.fromJson("not json").isEmpty())
    }
}
