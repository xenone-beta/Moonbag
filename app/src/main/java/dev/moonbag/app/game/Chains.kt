package dev.moonbag.app.game

enum class AddressStyle { HEX, BASE58, BECH }

/** An invented network. Each coin lives on one of them and moving coins costs that network's fee. */
data class Chain(
    val id: String,
    val name: String,
    val symbol: String,
    val color: Long,
    /** Network fee of one transfer, in play dollars. */
    val feeUsd: Double,
    val style: AddressStyle,
)

object Chains {

    val all: List<Chain> = listOf(
        Chain("moon", "Moonchain", "MNC", 0xFF7C4DFF, 0.40, AddressStyle.HEX),
        Chain("lunar", "Lunar L2", "LNR", 0xFF26A69A, 0.03, AddressStyle.HEX),
        Chain("orbit", "Orbit", "ORB", 0xFFFFA726, 0.002, AddressStyle.BASE58),
        Chain("nova", "Nova", "NVA", 0xFF42A5F5, 0.08, AddressStyle.BECH),
    )

    fun get(id: String): Chain? = all.firstOrNull { it.id == id }

    /** The network a coin lives on. Built-in coins are spread over the networks; your own coins live on Moonchain. */
    fun of(coin: CoinDef): Chain =
        if (coin.kind == CoinKind.CUSTOM) all[0] else all[Math.floorMod(coin.id.hashCode(), all.size)]
}
