package dev.moonbag.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/** The icons a coin can wear. Keys are what the game saves. */
object CoinIcons {

    private val all: Map<String, ImageVector> = mapOf(
        "rocket" to Icons.Filled.RocketLaunch,
        "moon" to Icons.Filled.NightsStay,
        "fire" to Icons.Filled.LocalFireDepartment,
        "paw" to Icons.Filled.Pets,
        "star" to Icons.Filled.Star,
        "bolt" to Icons.Filled.Bolt,
        "heart" to Icons.Filled.Favorite,
        "trophy" to Icons.Filled.EmojiEvents,
        "leaf" to Icons.Filled.Eco,
        "cake" to Icons.Filled.Cake,
        "globe" to Icons.Filled.Public,
        "brain" to Icons.Filled.Psychology,
        "shield" to Icons.Filled.Shield,
        "dice" to Icons.Filled.Casino,
        "smile" to Icons.Filled.SentimentVerySatisfied,
        "bulb" to Icons.Filled.Lightbulb,
        // used by built-in coins only
        "sun" to Icons.Filled.WbSunny,
        "pizza" to Icons.Filled.LocalPizza,
    )

    /** What the picker in the coin creator offers. */
    val choices: List<String> = listOf(
        "rocket", "moon", "fire", "paw", "star", "bolt", "heart", "trophy",
        "leaf", "cake", "globe", "brain", "shield", "dice", "smile", "bulb",
    )

    fun get(key: String): ImageVector = all[key] ?: Icons.Filled.Star
}
