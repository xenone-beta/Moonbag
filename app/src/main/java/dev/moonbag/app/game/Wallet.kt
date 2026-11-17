package dev.moonbag.app.game

import java.math.BigInteger
import java.security.MessageDigest
import java.util.Random

/**
 * The wallet identity of the game: twelve words and the addresses made from them. Nothing here touches a
 * real blockchain, and the words control no real money. Addresses are made for show and for the
 * invented networks only.
 */
/** The wallet the player has: its words (empty when there is none yet) and the addresses made from them. */
data class WalletInfo(val words: List<String>, val backedUp: Boolean) {
    val exists: Boolean get() = words.isNotEmpty()
    val addresses: Map<String, String> = if (words.isEmpty()) emptyMap() else Wallet.addresses(words)
    val ownAddresses: Set<String> = addresses.values.map { Wallet.canonical(it) }.toSet()
}

object Wallet {

    const val WORD_COUNT = 12

    private const val BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val BECH = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val NOVA_PREFIX = "nova1"

    private val hexPattern = Regex("^0x[0-9a-fA-F]{40}$")
    private val basePattern = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")
    private val bechPattern = Regex("^nova1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]{38}$")

    fun generate(random: Random): List<String> = List(WORD_COUNT) { WordList.words[random.nextInt(WordList.words.size)] }

    /** Splits what the user typed into lower-case words. */
    fun normalize(text: String): List<String> =
        text.lowercase().split(Regex("[\\s,;]+")).filter { it.isNotEmpty() }

    fun isValid(words: List<String>): Boolean = words.size == WORD_COUNT && words.all { it in WordList.set }

    private fun digest(words: List<String>, chain: Chain): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(("moonbag|" + words.joinToString(" ") + "|" + chain.id).toByteArray(Charsets.UTF_8))

    fun address(words: List<String>, chain: Chain): String {
        val bytes = digest(words, chain)
        return when (chain.style) {
            AddressStyle.HEX -> "0x" + bytes.take(20).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            AddressStyle.BASE58 -> base58(bytes)
            AddressStyle.BECH -> NOVA_PREFIX + bech(bytes, 38)
        }
    }

    fun addresses(words: List<String>): Map<String, String> = Chains.all.associate { it.id to address(words, it) }

    fun isAddress(chain: Chain, text: String): Boolean {
        val t = text.trim()
        return when (chain.style) {
            AddressStyle.HEX -> hexPattern.matches(t)
            AddressStyle.BASE58 -> basePattern.matches(t)
            AddressStyle.BECH -> bechPattern.matches(t)
        }
    }

    /** "0x12ab…9f3c". */
    fun shorten(address: String): String =
        if (address.length <= 14) address else address.take(6) + "…" + address.takeLast(4)

    /** An address as the game compares it: hex addresses ignore letter case. */
    fun canonical(address: String): String = address.trim().let { if (it.startsWith("0x")) it.lowercase() else it }

    /** The address a made-up trader (a leaderboard rival) has on [chain]. */
    fun rivalAddress(rivalId: String, chain: Chain): String =
        address(listOf("rival", rivalId), chain)

    private fun base58(bytes: ByteArray): String {
        var n = BigInteger(1, bytes)
        val base = BigInteger.valueOf(58)
        val out = StringBuilder()
        while (n.signum() > 0) {
            val (q, r) = n.divideAndRemainder(base)
            out.append(BASE58[r.toInt()])
            n = q
        }
        // Keep the length in the accepted range even for unlucky small numbers.
        while (out.length < 32) out.append(BASE58[0])
        return out.reverse().toString()
    }

    private fun bech(bytes: ByteArray, length: Int): String {
        val out = StringBuilder()
        var acc = 0
        var bits = 0
        for (b in bytes) {
            acc = (acc shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5 && out.length < length) {
                bits -= 5
                out.append(BECH[(acc shr bits) and 31])
            }
        }
        while (out.length < length) out.append(BECH[0])
        return out.toString()
    }
}
