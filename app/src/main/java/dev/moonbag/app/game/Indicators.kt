package dev.moonbag.app.game

data class Candle(val open: Double, val high: Double, val low: Double, val close: Double)

/** Chart maths: candles and the usual indicators. Lists keep the length of their input; early slots are null. */
object Indicators {

    /** Groups [points] into candles of [perCandle] points. Each candle opens where the last one closed. */
    fun candles(points: List<Double>, perCandle: Int): List<Candle> {
        if (points.isEmpty() || perCandle < 1) return emptyList()
        val out = ArrayList<Candle>()
        var previousClose = points.first()
        var i = 0
        while (i < points.size) {
            val chunk = points.subList(i, minOf(i + perCandle, points.size))
            val open = previousClose
            val close = chunk.last()
            out += Candle(open, maxOf(open, chunk.max()), minOf(open, chunk.min()), close)
            previousClose = close
            i += perCandle
        }
        return out
    }

    /** Simple moving average. */
    fun sma(values: List<Double>, period: Int): List<Double?> {
        if (period < 1) return values.map { null }
        val out = ArrayList<Double?>(values.size)
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= period) sum -= values[i - period]
            out += if (i >= period - 1) sum / period else null
        }
        return out
    }

    /** Exponential moving average, started from the simple average of the first [period] values. */
    fun ema(values: List<Double>, period: Int): List<Double?> {
        if (period < 1 || values.size < period) return values.map { null }
        val k = 2.0 / (period + 1)
        val out = ArrayList<Double?>(values.size)
        repeat(period - 1) { out += null }
        var ema = values.take(period).average()
        out += ema
        for (i in period until values.size) {
            ema = values[i] * k + ema * (1 - k)
            out += ema
        }
        return out
    }

    /** Relative strength index (Wilder smoothing), 0 to 100. */
    fun rsi(values: List<Double>, period: Int = 14): List<Double?> {
        if (period < 1 || values.size <= period) return values.map { null }
        val out = ArrayList<Double?>(values.size)
        repeat(period) { out += null }
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val d = values[i] - values[i - 1]
            if (d > 0) gain += d else loss -= d
        }
        gain /= period
        loss /= period
        out += strength(gain, loss)
        for (i in period + 1 until values.size) {
            val d = values[i] - values[i - 1]
            gain = (gain * (period - 1) + maxOf(d, 0.0)) / period
            loss = (loss * (period - 1) + maxOf(-d, 0.0)) / period
            out += strength(gain, loss)
        }
        return out
    }

    private fun strength(gain: Double, loss: Double): Double = when {
        loss == 0.0 && gain == 0.0 -> 50.0
        loss == 0.0 -> 100.0
        else -> 100.0 - 100.0 / (1.0 + gain / loss)
    }
}
