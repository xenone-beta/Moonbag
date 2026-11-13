package dev.moonbag.app.game

enum class CoinKind { MAJOR, MEME, CUSTOM }

/**
 * A coin in the game. Every price comes from a local simulation and nothing can be bought or sold for
 * real money. The meme coins are made up; the "major" coins borrow real names but their prices here are
 * invented, not market data.
 */
data class CoinDef(
    val id: String,
    val symbol: String,
    val name: String,
    val emoji: String,
    /** ARGB colour of the avatar and chart accents. */
    val color: Long,
    /** Starting price in play dollars. It is also the long-run level the price drifts back to. */
    val startPrice: Double,
    val supply: Double,
    /** Standard deviation of the log-return per tick (one tick = [PriceEngine.TICK_SECONDS] seconds). */
    val volatility: Double,
    /** Chance per tick of a sudden pump or dump. */
    val jumpChance: Double,
    /** Chance per tick of a "rug pull" (price collapses by 75 to 96 percent). */
    val rugChance: Double,
    /** How strongly the coin follows the overall market mood. */
    val beta: Double,
    /** 1 = calm, 4 = extremely risky. */
    val risk: Int,
    val kind: CoinKind = CoinKind.MEME,
    /** Scales the size of pumps and dumps (majors move far less than memes). */
    val jumpScale: Double = 1.0,
    /** Dollar depth of the pool for thin markets (your own coins). 0 means trades do not move the price. */
    val liquidity: Double = 0.0,
    /** Listing certificate number of a coin you created; 0 for built-in coins. */
    val serial: Int = 0,
    val createdAt: Long = 0L,
)

object Coins {

    private fun major(
        id: String, symbol: String, name: String, glyph: String, color: Long, price: Double, supply: Double,
        vol: Double, beta: Double, risk: Int,
    ) = CoinDef(
        id, symbol, name, glyph, color, price, supply, vol, 0.0004, 0.0, beta, risk,
        kind = CoinKind.MAJOR, jumpScale = 0.25,
    )

    val majors: List<CoinDef> = listOf(
        major("btc", "BTC", "Bitcoin", "₿", 0xFFF7931A, 95_000.0, 19.8e6, 0.0025, 0.15, 1),
        major("eth", "ETH", "Ethereum", "Ξ", 0xFF8A92B2, 3_200.0, 120e6, 0.0032, 0.20, 1),
        major("bnb", "BNB", "BNB", "◆", 0xFFF3BA2F, 620.0, 145e6, 0.0030, 0.20, 1),
        major("sol", "SOL", "Solana", "◎", 0xFF9945FF, 180.0, 480e6, 0.0048, 0.30, 2),
        major("xrp", "XRP", "XRP", "✕", 0xFF8EA0B4, 2.3, 58e9, 0.0045, 0.25, 2),
        major("ton", "TON", "Toncoin", "🔷", 0xFF0098EA, 5.5, 2.5e9, 0.0045, 0.25, 2),
        major("ada", "ADA", "Cardano", "₳", 0xFF3468D1, 0.8, 36e9, 0.0048, 0.25, 2),
        major("doge", "DOGE", "Dogecoin", "Ð", 0xFFC2A633, 0.35, 148e9, 0.0065, 0.40, 2),
        major("ltc", "LTC", "Litecoin", "Ł", 0xFF9AA6B2, 90.0, 75e6, 0.0036, 0.20, 1),
        major("trx", "TRX", "Tron", "▲", 0xFFEB0029, 0.25, 86e9, 0.0038, 0.20, 1),
    )

    val memes: List<CoinDef> = listOf(
        CoinDef("woof", "WOOF", "Woofcoin", "🐶", 0xFFE0A15B, 0.0000421, 420e12, 0.012, 0.0015, 0.0, 1.2, 2),
        CoinDef("kekw", "KEKW", "Kekwave", "😂", 0xFFF2C94C, 0.0183, 8.9e9, 0.010, 0.0010, 0.0, 1.0, 2),
        CoinDef("frogg", "FRGG", "Frogge", "🐸", 0xFF4CAF50, 0.00276, 69e9, 0.014, 0.0020, 0.00003, 1.4, 3),
        CoinDef("giga", "GIGA", "Gigachad", "🗿", 0xFF90A4AE, 0.412, 1e9, 0.008, 0.0008, 0.0, 0.9, 1),
        CoinDef("bnna", "BNNA", "Bananacoin", "🍌", 0xFFFFD54F, 1.27, 500e6, 0.009, 0.0010, 0.0, 1.0, 1),
        CoinDef("pzza", "PZZA", "Pizzapunk", "🍕", 0xFFEF6C00, 0.0912, 2e9, 0.011, 0.0012, 0.0, 1.1, 2),
        CoinDef("capy", "CAPY", "Capybara", "🦫", 0xFF8D6E63, 0.0567, 3e9, 0.006, 0.0005, 0.0, 0.7, 1),
        CoinDef("rham", "RHAM", "Rocket Hamster", "🐹", 0xFFFF8A65, 0.000318, 90e12, 0.020, 0.0030, 0.00005, 1.6, 3),
        CoinDef("nyan", "NYAN", "Sushi Cat", "🐱", 0xFFEC407A, 0.0034, 12e9, 0.013, 0.0018, 0.00002, 1.3, 3),
        CoinDef("dhnd", "DHND", "Diamond Hands", "💎", 0xFF4FC3F7, 3.84, 21e6, 0.007, 0.0007, 0.0, 0.8, 1),
        CoinDef("yolo", "YOLO", "YOLO Inu", "🎰", 0xFFAB47BC, 0.00000917, 1e15, 0.026, 0.0040, 0.0001, 1.8, 4),
        CoinDef("safe", "SAFE", "Totally Safe", "🛟", 0xFF26A69A, 0.00000052, 1e18, 0.030, 0.0050, 0.0002, 2.0, 4),
    )

    /** The built-in coins: majors first, then the memes. */
    val all: List<CoinDef> = majors + memes

    private val byId: Map<String, CoinDef> = all.associateBy { it.id }

    /** A built-in coin by id. Coins you created are looked up with [GameState.coin]. */
    fun get(id: String): CoinDef? = byId[id]
}
