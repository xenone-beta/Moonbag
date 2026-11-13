package dev.moonbag.app.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Offline price simulation.
 *
 * Each coin follows an Ornstein-Uhlenbeck process on its log price (a random walk that is gently
 * pulled back to the coin's base price), shifted by a shared market mood, plus rare pumps, dumps
 * and rug pulls. The exact discretisation makes one big step equal to many small ones, which is
 * what lets the market "run" while the app was closed.
 */
object PriceEngine {

    const val TICK_SECONDS = 2.0
    const val MAX_POINTS = 720

    /** Longest stretch of offline time that is simulated. */
    const val MAX_OFFLINE_SECONDS = 7L * 24 * 3600

    private const val REVERT = 0.002
    private const val MOOD_DECAY = 0.98
    private const val MOOD_NOISE = 0.12
    private const val MOOD_PUSH = 0.0015
    private const val MAX_EVENTS = 30

    fun gaussian(random: Random): Double {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    /** Moves the market forward by [dt] ticks (1.0 is one normal tick). */
    fun advance(
        state: GameState,
        dt: Double,
        now: Long,
        random: Random,
        recordEvents: Boolean = true,
    ): GameState {
        val mood = (state.sentiment * MOOD_DECAY.pow(dt) + MOOD_NOISE * sqrt(dt) * gaussian(random))
            .coerceIn(-3.0, 3.0)
        val pull = exp(-REVERT * dt)
        val spread = sqrt((1.0 - exp(-2.0 * REVERT * dt)) / (2.0 * REVERT))

        val prices = HashMap<String, Double>(state.prices)
        val history = HashMap<String, List<Double>>(state.history)
        val events = ArrayList<MarketEvent>()

        for (coin in state.allCoins) {
            val current = state.price(coin.id).coerceAtLeast(MIN_PRICE)
            val anchor = ln(coin.startPrice)
            var logPrice = anchor + (ln(current) - anchor) * pull +
                coin.beta * mood * MOOD_PUSH * dt +
                coin.volatility * spread * gaussian(random)

            var eventKind: EventKind? = null
            var eventLog = 0.0
            if (random.nextDouble() < (coin.jumpChance * dt).coerceAtMost(0.6)) {
                val up = random.nextDouble() < 0.58
                val size = ((0.08 + random.nextDouble() * 0.12 * coin.risk) * coin.jumpScale).coerceAtMost(0.6)
                eventLog = if (up) ln(1.0 + size) else ln(1.0 - size * 0.8)
                eventKind = if (up) EventKind.PUMP else EventKind.DUMP
                logPrice += eventLog
            }
            if (coin.rugChance > 0 && random.nextDouble() < (coin.rugChance * dt).coerceAtMost(0.5)) {
                eventLog = ln(0.04 + random.nextDouble() * 0.21)
                eventKind = EventKind.RUG
                logPrice += eventLog
            }

            val next = exp(logPrice.coerceIn(anchor - 5.0, anchor + 4.0)).coerceAtLeast(MIN_PRICE)
            prices[coin.id] = next
            history[coin.id] = (state.history[coin.id].orEmpty() + next).takeLast(MAX_POINTS)
            if (recordEvents && eventKind != null) {
                events += MarketEvent(now, coin.id, eventKind, exp(eventLog) - 1.0)
            }
        }

        val moved = state.copy(
            prices = prices,
            history = history,
            sentiment = mood,
            events = if (events.isEmpty()) state.events else (events + state.events).take(MAX_EVENTS),
        )
        return moved.copy(netHistory = (state.netHistory + moved.netWorth()).takeLast(MAX_POINTS))
    }

    /** Simulates [elapsedSeconds] of closed-app time in at most [MAX_STEPS] big steps. */
    fun catchUp(state: GameState, elapsedSeconds: Long, now: Long, random: Random): GameState {
        val seconds = elapsedSeconds.coerceIn(0L, MAX_OFFLINE_SECONDS)
        val ticks = seconds / TICK_SECONDS
        if (ticks < 1.0) return state
        val steps = ticks.toInt().coerceIn(1, MAX_STEPS)
        val dt = ticks / steps
        var result = state
        repeat(steps) { result = advance(result, dt, now, random) }
        return result
    }

    private const val MAX_STEPS = 240
    private const val MIN_PRICE = 1e-12
}
