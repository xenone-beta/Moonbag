package dev.moonbag.app.game

import android.content.Context

/** Keeps the save and the two settings in SharedPreferences. */
class GameStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("moonbag", Context.MODE_PRIVATE)

    fun load(): GameState? {
        val text = prefs.getString(KEY_SAVE, null) ?: return null
        return runCatching { GameJson.fromJson(text) }.getOrNull()
    }

    fun save(state: GameState) {
        prefs.edit().putString(KEY_SAVE, GameJson.toJson(state)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_SAVE).apply()
    }

    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC, true)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC, value).apply()

    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    private companion object {
        const val KEY_SAVE = "save"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_HAPTICS = "haptics"
    }
}
