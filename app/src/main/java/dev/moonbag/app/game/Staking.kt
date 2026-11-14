package dev.moonbag.app.game

import kotlin.math.exp

/** Locking coins to earn more of the same coin. Rewards keep coming while the app is closed. */
object Staking {

    /** Longest stretch of closed-app time that earns staking rewards. */
    const val MAX_OFFLINE_SECONDS = 12L * 3600

    /** Share of the staked amount earned per hour. Riskier coins pay more. */
    fun ratePerHour(coin: CoinDef): Double = when (coin.kind) {
        CoinKind.MAJOR -> 0.004
        CoinKind.MEME -> 0.008
        CoinKind.CUSTOM -> 0.012
    }

    /** Locks [amount] coins. Returns null if you do not have that many free coins. */
    fun stake(state: GameState, coinId: String, amount: Double): GameState? {
        val held = state.holdings[coinId] ?: return null
        if (!amount.isFinite() || amount <= 0.0 || amount > held.liquid * (1 + 1e-9)) return null
        val locked = minOf(amount, held.liquid)
        return state.copy(holdings = state.holdings + (coinId to held.copy(staked = held.staked + locked)))
    }

    /** Frees [amount] staked coins. Returns null if that much is not staked. */
    fun unstake(state: GameState, coinId: String, amount: Double): GameState? {
        val held = state.holdings[coinId] ?: return null
        if (!amount.isFinite() || amount <= 0.0 || amount > held.staked * (1 + 1e-9)) return null
        val freed = minOf(amount, held.staked)
        return state.copy(holdings = state.holdings + (coinId to held.copy(staked = (held.staked - freed).coerceAtLeast(0.0))))
    }

    /** Pays out the rewards for [seconds] of staking, compounding continuously. */
    fun accrue(state: GameState, seconds: Double): GameState {
        if (seconds <= 0.0) return state
        var earnedUsd = 0.0
        val holdings = state.holdings.mapValues { (id, h) ->
            if (h.staked <= 0.0) return@mapValues h
            val coin = state.coin(id) ?: return@mapValues h
            val reward = h.staked * (exp(ratePerHour(coin) * seconds / 3600.0) - 1.0)
            earnedUsd += reward * state.price(id)
            h.copy(amount = h.amount + reward, staked = h.staked + reward)
        }
        if (earnedUsd == 0.0) return state
        return state.copy(holdings = holdings, stakingEarned = state.stakingEarned + earnedUsd)
    }
}
