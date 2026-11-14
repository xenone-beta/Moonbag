package dev.moonbag.app.game

enum class CampaignKind { MARKETING, BURN }

/** A temporary push on one coin's price. [strength] is in the same units as market events. */
data class Campaign(
    val coinId: String,
    val kind: CampaignKind,
    val strength: Double,
    val ticksLeft: Double,
    val totalTicks: Double,
) {
    val progress: Double get() = if (totalTicks > 0) (ticksLeft / totalTicks).coerceIn(0.0, 1.0) else 0.0
}

enum class MarketingTier(val cost: Double, val strength: Double, val ticks: Double) {
    SMALL(500.0, 2.5, 150.0),
    MEDIUM(2_000.0, 5.0, 200.0),
    BIG(10_000.0, 9.0, 240.0),
}

/** Things you can do with a coin you created. */
object CoinManagement {

    /** Chance that the hype of a marketing campaign ends in a sell-off. */
    const val BACKLASH_CHANCE = 0.35
    const val MAX_BURN_STRENGTH = 4.0
    const val BURN_TICKS = 120.0

    fun isCustom(state: GameState, coinId: String) = state.customCoins.any { it.id == coinId }

    fun marketingActive(state: GameState, coinId: String): Boolean =
        state.campaigns.any { it.coinId == coinId && it.kind == CampaignKind.MARKETING }

    /**
     * Strength-ticks of push on a coin over a step of [dt] ticks. A campaign only counts for the time
     * it has left, so long offline steps do not overshoot.
     */
    fun push(campaigns: List<Campaign>, coinId: String, dt: Double): Double =
        campaigns.filter { it.coinId == coinId }
            .sumOf { it.strength * minOf(dt, it.ticksLeft).coerceAtLeast(0.0) }
            .coerceAtMost(12.0 * dt)

    /** Pays for a campaign. Returns null if the coin is not yours, you cannot pay, or one is running. */
    fun startMarketing(state: GameState, coinId: String, tier: MarketingTier): GameState? {
        if (!isCustom(state, coinId) || marketingActive(state, coinId)) return null
        if (state.cash + 1e-9 < tier.cost) return null
        return state.copy(
            cash = (state.cash - tier.cost).coerceAtLeast(0.0),
            campaigns = state.campaigns + Campaign(coinId, CampaignKind.MARKETING, tier.strength, tier.ticks, tier.ticks),
        )
    }

    /** How strong the short burn hype would be for burning [amount] coins. */
    fun burnStrength(state: GameState, coinId: String, amount: Double): Double {
        val coin = state.coin(coinId) ?: return 0.0
        if (coin.liquidity <= 0.0) return 0.0
        return (8.0 * amount * state.price(coinId) / coin.liquidity).coerceAtMost(MAX_BURN_STRENGTH)
    }

    /**
     * Destroys [amount] of your own coins. They are gone for good (a realised loss) and the burn
     * gives the price a short push. Returns null if you do not have that many free coins.
     */
    fun burn(state: GameState, coinId: String, amount: Double): GameState? {
        if (!isCustom(state, coinId)) return null
        val held = state.holdings[coinId] ?: return null
        if (!amount.isFinite() || amount <= 0.0 || amount > held.liquid * (1 + 1e-9)) return null
        val burned = minOf(amount, held.liquid)
        val closing = held.staked <= 0.0 && burned >= held.amount * (1 - 1e-9)
        val removedBasis = if (closing) held.costBasis else held.costBasis * (burned / held.amount)
        val holdings = if (closing) {
            state.holdings - coinId
        } else {
            state.holdings + (coinId to Holding(held.amount - burned, held.costBasis - removedBasis, held.staked))
        }
        val strength = burnStrength(state, coinId, burned)
        return state.copy(
            holdings = holdings,
            realizedPnl = state.realizedPnl - removedBasis,
            customCoins = state.customCoins.map { if (it.id == coinId) it.copy(burned = it.burned + burned) else it },
            campaigns = if (strength > 0.0) {
                state.campaigns + Campaign(coinId, CampaignKind.BURN, strength, BURN_TICKS, BURN_TICKS)
            } else {
                state.campaigns
            },
        )
    }
}
