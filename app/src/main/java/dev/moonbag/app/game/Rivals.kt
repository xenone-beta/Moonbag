package dev.moonbag.app.game

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

enum class RivalStyle { ROOKIE, STEADY, DEGEN, WHALE }

/**
 * An invented trader for the local leaderboard. Their capital is a fixed function of the days since
 * your game began, so nothing needs to be saved and the board is the same every time you open it.
 */
data class Rival(
    val id: String,
    val name: String,
    val color: Long,
    val style: RivalStyle,
    val start: Double,
    /** Compound growth per day. */
    val rate: Double,
    /** Size of the ups and downs (log scale). */
    val amplitude: Double,
    /** Length of the main swing in days. */
    val period: Double,
    val phase: Double,
)

data class BoardEntry(
    val id: String,
    val name: String,
    val color: Long,
    val style: RivalStyle?,
    val netWorth: Double,
    val isPlayer: Boolean,
)

object Rivals {

    const val PLAYER_ID = "you"
    private const val MAX_DAYS = 365.0
    private const val MIN_WORTH = 5.0

    val all: List<Rival> = listOf(
        Rival("nina", "Noob Nina", 0xFFEC407A, RivalStyle.ROOKIE, 40.0, 0.030, 0.25, 1.3, 0.4),
        Rival("sam", "Sleepy Sam", 0xFF78909C, RivalStyle.ROOKIE, 120.0, 0.025, 0.12, 2.1, 2.0),
        Rival("pete", "Paper Pete", 0xFFFFB74D, RivalStyle.ROOKIE, 200.0, 0.035, 0.30, 0.9, 4.1),
        Rival("hank", "Hodl Hank", 0xFF66BB6A, RivalStyle.STEADY, 600.0, 0.055, 0.10, 3.0, 1.1),
        Rival("marta", "Moon Marta", 0xFF9575DE, RivalStyle.STEADY, 1_500.0, 0.060, 0.18, 2.4, 3.3),
        Rival("gus", "Gigabrain Gus", 0xFF4FC3F7, RivalStyle.STEADY, 4_000.0, 0.050, 0.14, 3.6, 5.2),
        Rival("rick", "Rug Rick", 0xFFEF5350, RivalStyle.DEGEN, 300.0, 0.090, 0.70, 0.7, 0.9),
        Rival("lady", "Lady Rekt", 0xFFAB47BC, RivalStyle.DEGEN, 900.0, 0.085, 0.65, 0.5, 2.7),
        Rival("yolo", "YOLO Yuri", 0xFFFF7043, RivalStyle.DEGEN, 2_500.0, 0.080, 0.80, 0.8, 5.9),
        Rival("panda", "Pump Panda", 0xFF26A69A, RivalStyle.WHALE, 40_000.0, 0.045, 0.20, 4.0, 1.7),
        Rival("wanda", "Whale Wanda", 0xFF42A5F5, RivalStyle.WHALE, 120_000.0, 0.040, 0.15, 5.0, 3.9),
        Rival("neko", "Satoshi Neko", 0xFFFFCA28, RivalStyle.WHALE, 500_000.0, 0.035, 0.12, 6.0, 0.3),
    )

    fun daysSinceStart(state: GameState, now: Long): Double =
        if (state.startedAt <= 0L) 0.0 else ((now - state.startedAt) / 86_400_000.0).coerceIn(0.0, MAX_DAYS)

    /** What [rival] is worth [days] days into the game. */
    fun netWorth(rival: Rival, days: Double): Double {
        val d = days.coerceIn(0.0, MAX_DAYS)
        val swing = sin(2 * PI * d / rival.period + rival.phase) + 0.5 * sin(2 * PI * d / (rival.period * 0.37) + rival.phase * 1.7)
        return (rival.start * (1.0 + rival.rate).pow(d) * exp(rival.amplitude * swing)).coerceAtLeast(MIN_WORTH)
    }

    /** Everyone on the board, richest first, with you included. */
    fun board(state: GameState, now: Long, playerName: String): List<BoardEntry> {
        val days = daysSinceStart(state, now)
        val entries = all.map { BoardEntry(it.id, it.name, it.color, it.style, netWorth(it, days), false) } +
            BoardEntry(PLAYER_ID, playerName, 0xFF7C4DFF, null, state.netWorth(), true)
        return entries.sortedByDescending { it.netWorth }
    }

    /** Your place, starting at 1. */
    fun rank(state: GameState, now: Long): Int =
        board(state, now, "").indexOfFirst { it.isPlayer } + 1
}
