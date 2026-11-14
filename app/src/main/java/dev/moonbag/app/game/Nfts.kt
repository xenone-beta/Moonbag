package dev.moonbag.app.game

import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

enum class Rarity(val multiplier: Double, val weight: Int) {
    COMMON(0.4, 70),
    RARE(1.2, 22),
    EPIC(3.0, 6),
    LEGENDARY(10.0, 2),
}

data class NftCollection(val id: String, val mintPrice: Double, val volatility: Double)

/** One collectible. Its look is drawn from [seed]; its value follows the collection's floor price. */
data class Nft(
    val id: Long,
    val collection: String,
    val seed: Int,
    val rarity: Rarity,
    val mintedAt: Long,
    val paid: Double,
)

sealed interface MintResult {
    data class Minted(val nft: Nft) : MintResult
    data object NotEnoughCash : MintResult
    data object LimitReached : MintResult
}

object Nfts {

    const val SALE_FEE = 0.025
    const val MAX_OWNED = 60
    private const val REVERT = 0.001
    private const val MOOD_PUSH = 0.0004
    private const val MAX_POINTS = 240

    val collections: List<NftCollection> = listOf(
        NftCollection("pixel", 50.0, 0.004),
        NftCollection("orbs", 300.0, 0.005),
        NftCollection("rocks", 2000.0, 0.006),
    )

    fun collection(id: String): NftCollection? = collections.firstOrNull { it.id == id }

    fun floor(state: GameState, collectionId: String): Double =
        state.nftFloors[collectionId] ?: collection(collectionId)?.mintPrice ?: 0.0

    /** A small, fixed difference between pieces of the same rarity (0.9 to 1.1). */
    private fun trait(seed: Int): Double = 0.9 + (seed and 0xFF) / 255.0 * 0.2

    fun value(state: GameState, nft: Nft): Double = floor(state, nft.collection) * nft.rarity.multiplier * trait(nft.seed)

    fun floorChange(state: GameState, collectionId: String): Double {
        val first = state.nftHistory[collectionId]?.firstOrNull() ?: return 0.0
        return if (first > 0) floor(state, collectionId) / first - 1.0 else 0.0
    }

    fun rollRarity(random: Random): Rarity {
        var roll = random.nextInt(Rarity.entries.sumOf { it.weight })
        for (r in Rarity.entries) {
            if (roll < r.weight) return r
            roll -= r.weight
        }
        return Rarity.COMMON
    }

    /** Pays the mint price and gets a random piece of the collection. */
    fun mint(state: GameState, collectionId: String, now: Long, random: Random): Pair<GameState, MintResult> {
        val col = collection(collectionId) ?: return state to MintResult.NotEnoughCash
        if (state.nfts.size >= MAX_OWNED) return state to MintResult.LimitReached
        if (state.cash + 1e-9 < col.mintPrice) return state to MintResult.NotEnoughCash
        val nft = Nft(state.nextNftId, collectionId, random.nextInt(), rollRarity(random), now, col.mintPrice)
        val next = state.copy(
            cash = (state.cash - col.mintPrice).coerceAtLeast(0.0),
            nfts = listOf(nft) + state.nfts,
            nextNftId = state.nextNftId + 1,
        )
        return next to MintResult.Minted(nft)
    }

    /** Sells a piece at its current value minus the marketplace fee. Returns the proceeds, or null. */
    fun sell(state: GameState, nftId: Long): Pair<GameState, Double?> {
        val nft = state.nfts.firstOrNull { it.id == nftId } ?: return state to null
        val gross = value(state, nft)
        val fee = gross * SALE_FEE
        val proceeds = gross - fee
        val next = state.copy(
            cash = state.cash + proceeds,
            nfts = state.nfts.filterNot { it.id == nftId },
            realizedPnl = state.realizedPnl + (proceeds - nft.paid),
            feesPaid = state.feesPaid + fee,
        )
        return next to proceeds
    }

    /** Moves the floor prices: a slow random walk around the mint price, nudged by the market mood. */
    fun advanceFloors(state: GameState, dt: Double, mood: Double, random: Random): GameState {
        val pull = exp(-REVERT * dt)
        val spread = kotlin.math.sqrt((1.0 - exp(-2.0 * REVERT * dt)) / (2.0 * REVERT))
        val floors = HashMap<String, Double>(state.nftFloors)
        val history = HashMap<String, List<Double>>(state.nftHistory)
        for (col in collections) {
            val anchor = ln(col.mintPrice)
            val current = floor(state, col.id).coerceAtLeast(1e-6)
            val log = anchor + (ln(current) - anchor) * pull + mood * MOOD_PUSH * dt +
                col.volatility * spread * PriceEngine.gaussian(random)
            val next = exp(log.coerceIn(anchor - 1.5, anchor + 1.5))
            floors[col.id] = next
            history[col.id] = (state.nftHistory[col.id].orEmpty() + next).takeLast(MAX_POINTS)
        }
        return state.copy(nftFloors = floors, nftHistory = history)
    }
}
