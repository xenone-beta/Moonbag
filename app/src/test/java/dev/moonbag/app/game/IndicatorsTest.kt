package dev.moonbag.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorsTest {

    @Test
    fun candlesChainAndCoverTheRange() {
        val points = listOf(10.0, 12.0, 9.0, 11.0, 13.0, 8.0, 14.0)
        val c = Indicators.candles(points, 3)
        assertEquals(3, c.size)
        assertEquals(Candle(10.0, 12.0, 9.0, 9.0), c[0])
        assertEquals(9.0, c[1].open, 0.0)
        assertEquals(8.0, c[1].low, 0.0)
        assertEquals(13.0, c[1].high, 0.0)
        assertEquals(14.0, c[2].close, 0.0)
        assertTrue(c.all { it.low <= minOf(it.open, it.close) && it.high >= maxOf(it.open, it.close) })
        assertTrue(Indicators.candles(emptyList(), 3).isEmpty())
    }

    @Test
    fun smaMatchesHandCalculation() {
        val sma = Indicators.sma(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 3)
        assertNull(sma[0]); assertNull(sma[1])
        assertEquals(2.0, sma[2]!!, 1e-12)
        assertEquals(3.0, sma[3]!!, 1e-12)
        assertEquals(4.0, sma[4]!!, 1e-12)
    }

    @Test
    fun emaStartsFromTheAverageAndFollowsThePrice() {
        val ema = Indicators.ema(listOf(2.0, 4.0, 6.0, 8.0), 2)
        assertNull(ema[0])
        assertEquals(3.0, ema[1]!!, 1e-12)
        // k = 2/3: 6*2/3 + 3/3 = 5
        assertEquals(5.0, ema[2]!!, 1e-12)
        assertEquals(7.0, ema[3]!!, 1e-12)
    }

    @Test
    fun rsiIsBoundedAndExtremeOnStraightMoves() {
        val up = Indicators.rsi(List(30) { it.toDouble() }, 14)
        assertNull(up[13])
        assertEquals(100.0, up.last()!!, 1e-9)
        val down = Indicators.rsi(List(30) { 100.0 - it }, 14)
        assertEquals(0.0, down.last()!!, 1e-9)
        val flat = Indicators.rsi(List(30) { 5.0 }, 14)
        assertEquals(50.0, flat.last()!!, 1e-9)
        val noisy = Indicators.rsi(List(200) { 100.0 + Math.sin(it / 3.0) * 5 }, 14)
        assertTrue(noisy.filterNotNull().all { it in 0.0..100.0 })
        assertEquals(30, Indicators.sma(List(30) { 1.0 }, 50).size)
    }
}
