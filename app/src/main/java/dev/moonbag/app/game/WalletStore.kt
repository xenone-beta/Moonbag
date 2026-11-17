package dev.moonbag.app.game

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A saved recipient. */
data class Contact(val name: String, val address: String, val chainId: String)

/** Contacts as JSON, kept apart from Android so it can be tested. */
object ContactsJson {
    fun toJson(list: List<Contact>): String {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("name", it.name).put("address", it.address).put("chain", it.chainId)) }
        return a.toString()
    }

    fun fromJson(text: String): List<Contact> = try {
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val chain = Chains.get(o.optString("chain")) ?: return@mapNotNull null
            val address = o.optString("address")
            val name = o.optString("name").trim()
            if (name.isEmpty() || !Wallet.isAddress(chain, address)) null else Contact(name, Wallet.canonical(address), chain.id)
        }
    } catch (_: org.json.JSONException) {
        emptyList()
    }
}

/**
 * Keeps the game wallet's twelve words, the backup flag and the contacts. The words stay in this app's
 * private storage. They are a game feature: they control no real money.
 */
class WalletStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("moonbag_wallet", Context.MODE_PRIVATE)

    var words: List<String>
        get() = Wallet.normalize(prefs.getString("words", "") ?: "").takeIf { Wallet.isValid(it) } ?: emptyList()
        set(v) = prefs.edit().putString("words", v.joinToString(" ")).apply()

    var backedUp: Boolean
        get() = prefs.getBoolean("backed_up", false)
        set(v) = prefs.edit().putBoolean("backed_up", v).apply()

    var contacts: List<Contact>
        get() = ContactsJson.fromJson(prefs.getString("contacts", "[]") ?: "[]")
        set(v) = prefs.edit().putString("contacts", ContactsJson.toJson(v)).apply()
}
