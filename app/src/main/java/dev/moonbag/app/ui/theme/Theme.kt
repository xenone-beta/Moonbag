package dev.moonbag.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette for Android 11 and older, or when dynamic colours are switched off.
private val LightColors = lightColorScheme(
    primary = Color(0xFF5B3FD0),
    primaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFF6A5F8F),
    tertiary = Color(0xFF8A5A00),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFC9BEFF),
    primaryContainer = Color(0xFF4529B5),
    secondary = Color(0xFFCDC2F2),
    tertiary = Color(0xFFFFB955),
)

@Composable
fun MoonbagTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You: colours follow the wallpaper on Android 12+.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** Green and red for price moves. They stay fixed so "up" and "down" never depend on the wallpaper. */
object MarketColors {
    @Composable
    fun up(): Color = if (isSystemInDarkTheme()) Color(0xFF3DDC97) else Color(0xFF0B8F5A)

    @Composable
    fun down(): Color = if (isSystemInDarkTheme()) Color(0xFFFF6B7A) else Color(0xFFC62840)

    @Composable
    fun forChange(change: Double): Color = if (change >= 0) up() else down()
}
