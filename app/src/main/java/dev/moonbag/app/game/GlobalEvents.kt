package dev.moonbag.app.game

enum class GlobalEventKind { BULL_RUN, BEAR_MARKET, MEME_SEASON, FLASH_CRASH }

/** A market-wide event that lasts for a while. [ticksLeft] counts down in market ticks. */
data class GlobalEvent(val kind: GlobalEventKind, val ticksLeft: Double, val totalTicks: Double) {
    val progress: Double get() = if (totalTicks > 0) (ticksLeft / totalTicks).coerceIn(0.0, 1.0) else 0.0

    /** True during the first step, when a flash crash hits. */
    val justStarted: Boolean get() = ticksLeft >= totalTicks
}

object GlobalEvents {

    /** Chance per tick that a new event starts while none is running (about one per 30 to 40 minutes). */
    const val START_CHANCE_PER_TICK = 1.0 / 1000

    fun duration(kind: GlobalEventKind): Double = when (kind) {
        GlobalEventKind.BULL_RUN -> 240.0
        GlobalEventKind.BEAR_MARKET -> 240.0
        GlobalEventKind.MEME_SEASON -> 200.0
        GlobalEventKind.FLASH_CRASH -> 90.0
    }

    /** How hard the event pushes a coin of the given kind (positive up, negative down). */
    fun bias(kind: GlobalEventKind, coin: CoinKind): Double = when (kind) {
        GlobalEventKind.BULL_RUN -> 2.0
        GlobalEventKind.BEAR_MARKET -> -2.0
        GlobalEventKind.MEME_SEASON -> if (coin == CoinKind.MAJOR) 0.3 else 2.5
        GlobalEventKind.FLASH_CRASH -> 0.0
    }

    fun volatilityFactor(kind: GlobalEventKind): Double = when (kind) {
        GlobalEventKind.BULL_RUN -> 1.2
        GlobalEventKind.BEAR_MARKET -> 1.3
        GlobalEventKind.MEME_SEASON -> 1.4
        GlobalEventKind.FLASH_CRASH -> 2.0
    }

    fun start(kind: GlobalEventKind): GlobalEvent = GlobalEvent(kind, duration(kind), duration(kind))

    /** Maybe starts an event during a step of [dt] ticks. */
    fun maybeStart(random: kotlin.random.Random, dt: Double): GlobalEvent? {
        if (random.nextDouble() >= (START_CHANCE_PER_TICK * dt).coerceAtMost(0.5)) return null
        return start(GlobalEventKind.entries[random.nextInt(GlobalEventKind.entries.size)])
    }
}
