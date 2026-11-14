package dev.moonbag.app.game

import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

enum class UpgradeKind { TAP, AUTO, CRIT, VAULT }

data class TapResult(val value: Double, val crit: Boolean)

/** A kind of mining farm: bought many times, each one adds [perSecond] dollars per second. */
data class FarmDef(val id: String, val icon: String, val basePrice: Double, val growth: Double, val perSecond: Double)

/** The "mine" clicker that produces the first play dollars. */
object Clicker {

    const val CRIT_MULTIPLIER = 5.0
    const val BASE_CRIT_CHANCE = 0.05
    const val MAX_CRIT_LEVEL = 45
    const val MAX_VAULT_LEVEL = 10
    private const val BASE_VAULT_HOURS = 2

    val FARMS: List<FarmDef> = listOf(
        FarmDef("gpu", "memory", 150.0, 1.12, 1.0),
        FarmDef("farm", "dns", 2_000.0, 1.13, 15.0),
        FarmDef("dc", "storage", 30_000.0, 1.14, 250.0),
        FarmDef("quantum", "cloud", 500_000.0, 1.15, 4_500.0),
    )

    fun farmCount(u: Upgrades, index: Int): Int = u.farms.getOrElse(index) { 0 }

    fun farmCost(index: Int, owned: Int): Double {
        val f = FARMS[index]
        return (f.basePrice * f.growth.pow(owned) * 100).roundToLong() / 100.0
    }

    fun farmIncome(u: Upgrades): Double = FARMS.indices.sumOf { farmCount(u, it) * FARMS[it].perSecond }

    /** Buys one more farm of the given kind, or returns null if it is too expensive. */
    fun buyFarm(state: GameState, index: Int): GameState? {
        if (index !in FARMS.indices) return null
        val owned = farmCount(state.upgrades, index)
        val price = farmCost(index, owned)
        if (state.cash + 1e-9 < price) return null
        val farms = List(FARMS.size) { farmCount(state.upgrades, it) + if (it == index) 1 else 0 }
        return state.copy(cash = (state.cash - price).coerceAtLeast(0.0), upgrades = state.upgrades.copy(farms = farms))
    }

    fun tapValue(u: Upgrades): Double = 1.0 + u.tap * (1.0 + u.tap * 0.15)

    fun perSecond(u: Upgrades): Double = u.auto * 0.5 * (1.0 + u.auto * 0.1) + farmIncome(u)

    fun critChance(u: Upgrades): Double = BASE_CRIT_CHANCE + 0.01 * u.crit

    /** Hours of offline mining the vault stores. */
    fun vaultHours(u: Upgrades): Int = BASE_VAULT_HOURS + u.vault

    fun level(u: Upgrades, kind: UpgradeKind): Int = when (kind) {
        UpgradeKind.TAP -> u.tap
        UpgradeKind.AUTO -> u.auto
        UpgradeKind.CRIT -> u.crit
        UpgradeKind.VAULT -> u.vault
    }

    fun maxLevel(kind: UpgradeKind): Int? = when (kind) {
        UpgradeKind.CRIT -> MAX_CRIT_LEVEL
        UpgradeKind.VAULT -> MAX_VAULT_LEVEL
        else -> null
    }

    fun isMaxed(u: Upgrades, kind: UpgradeKind): Boolean {
        val max = maxLevel(kind) ?: return false
        return level(u, kind) >= max
    }

    /** Price of the next level. */
    fun cost(kind: UpgradeKind, level: Int): Double {
        val raw = when (kind) {
            UpgradeKind.TAP -> 20.0 * 1.35.pow(level)
            UpgradeKind.AUTO -> 60.0 * 1.40.pow(level)
            UpgradeKind.CRIT -> 150.0 * 1.50.pow(level)
            UpgradeKind.VAULT -> 400.0 * 1.80.pow(level)
        }
        return (raw * 100).roundToLong() / 100.0
    }

    fun upgraded(u: Upgrades, kind: UpgradeKind): Upgrades = when (kind) {
        UpgradeKind.TAP -> u.copy(tap = u.tap + 1)
        UpgradeKind.AUTO -> u.copy(auto = u.auto + 1)
        UpgradeKind.CRIT -> u.copy(crit = u.crit + 1)
        UpgradeKind.VAULT -> u.copy(vault = u.vault + 1)
    }

    /** Buys the next level, or returns null if it is maxed out or too expensive. */
    fun buy(state: GameState, kind: UpgradeKind): GameState? {
        if (isMaxed(state.upgrades, kind)) return null
        val price = cost(kind, level(state.upgrades, kind))
        if (state.cash + 1e-9 < price) return null
        return state.copy(cash = (state.cash - price).coerceAtLeast(0.0), upgrades = upgraded(state.upgrades, kind))
    }

    fun tap(state: GameState, random: Random): Pair<GameState, TapResult> {
        val crit = random.nextDouble() < critChance(state.upgrades)
        val value = tapValue(state.upgrades) * if (crit) CRIT_MULTIPLIER else 1.0
        val next = state.copy(
            cash = state.cash + value,
            lifetimeEarned = state.lifetimeEarned + value,
            taps = state.taps + 1,
        )
        return next to TapResult(value, crit)
    }

    /** Income from the auto-miner over [seconds] of play. */
    fun earnPassive(state: GameState, seconds: Double): GameState {
        val gain = perSecond(state.upgrades) * seconds
        if (gain <= 0.0) return state
        return state.copy(cash = state.cash + gain, lifetimeEarned = state.lifetimeEarned + gain)
    }
}
