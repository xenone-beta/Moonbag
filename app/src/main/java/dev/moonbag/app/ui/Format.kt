package dev.moonbag.app.ui

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10

private fun f(pattern: String, vararg args: Any): String = String.format(Locale.getDefault(), pattern, *args)

/** 1234567 -> "1.23M". Used for large numbers where exact digits do not matter. */
fun formatCompact(value: Double): String {
    val a = abs(value)
    return when {
        a >= 1e15 -> f("%.2fQ", value / 1e15)
        a >= 1e12 -> f("%.2fT", value / 1e12)
        a >= 1e9 -> f("%.2fB", value / 1e9)
        a >= 1e6 -> f("%.2fM", value / 1e6)
        else -> f("%,.2f", value)
    }
}

/** Play-money amount: "$1,234.56", "$1.23M", "-$5.00". */
fun formatUsd(value: Double): String {
    val sign = if (value < -0.005) "-" else ""
    return sign + "$" + formatCompact(abs(value))
}

/** Signed play-money amount: "+$5.00", "-$5.00". */
fun formatSignedUsd(value: Double): String = (if (value >= 0.005) "+" else "") + formatUsd(value)

/** Price with enough decimals to show about four significant digits, down to 0.0000005. */
fun formatPrice(price: Double): String {
    if (!price.isFinite() || price <= 0) return "$0"
    return "$" + when {
        price >= 1000 -> f("%,.2f", price)
        price >= 100 -> f("%.2f", price)
        price >= 1 -> f("%.4f", price)
        else -> {
            val decimals = (ceil(-log10(price)).toInt() + 3).coerceIn(4, 12)
            f("%.${decimals}f", price)
        }
    }
}

/** Coin quantity: "1.25B", "12,345", "3.50", "0.004200". */
fun formatAmount(amount: Double): String = when {
    amount >= 1e6 -> formatCompact(amount)
    amount >= 1000 -> f("%,.0f", amount)
    amount >= 1 -> f("%.2f", amount)
    else -> f("%.6f", amount)
}

/** 0.0523 -> "+5.23%". */
fun formatPercent(fraction: Double): String {
    val sign = if (fraction >= 0.00005) "+" else ""
    val pct = fraction * 100
    return when {
        abs(pct) >= 1000 -> sign + f("%,.0f%%", pct)
        else -> sign + f("%.2f%%", pct)
    }
}

/** Short local time of day, e.g. "14:05". */
fun formatTime(millis: Long): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(millis))

/** Long local date, e.g. "13 November 2026". */
fun formatDate(millis: Long): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG).format(java.util.Date(millis))
