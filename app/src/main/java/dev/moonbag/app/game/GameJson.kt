package dev.moonbag.app.game

import org.json.JSONArray
import org.json.JSONObject

/** Save format. Tolerant on read: coins added in later versions get default values. */
object GameJson {

    private const val VERSION = 1

    fun toJson(state: GameState): String {
        val root = JSONObject()
            .put("version", VERSION)
            .put("cash", state.cash)
            .put("sentiment", state.sentiment)
            .put("lifetimeEarned", state.lifetimeEarned)
            .put("taps", state.taps)
            .put("realizedPnl", state.realizedPnl)
            .put("feesPaid", state.feesPaid)
            .put("savedAt", state.savedAt)
            .put("coinsListed", state.coinsListed)
            .put("granted", state.granted)
            .put(
                "customCoins",
                JSONArray().also { a ->
                    state.customCoins.forEach { c ->
                        a.put(
                            JSONObject()
                                .put("id", c.id).put("symbol", c.symbol).put("name", c.name)
                                .put("emoji", c.emoji).put("color", c.color).put("style", c.style.name)
                                .put("serial", c.serial).put("createdAt", c.createdAt),
                        )
                    }
                },
            )
            .put(
                "upgrades",
                JSONObject()
                    .put("tap", state.upgrades.tap)
                    .put("auto", state.upgrades.auto)
                    .put("crit", state.upgrades.crit)
                    .put("vault", state.upgrades.vault),
            )
            .put("prices", JSONObject().also { o -> state.prices.forEach { (k, v) -> o.put(k, v) } })
            .put("history", JSONObject().also { o -> state.history.forEach { (k, v) -> o.put(k, doubles(v)) } })
            .put("netHistory", doubles(state.netHistory))
            .put(
                "holdings",
                JSONObject().also { o ->
                    state.holdings.forEach { (k, h) ->
                        o.put(k, JSONObject().put("amount", h.amount).put("basis", h.costBasis))
                    }
                },
            )
            .put(
                "trades",
                JSONArray().also { a ->
                    state.trades.forEach { t ->
                        a.put(
                            JSONObject()
                                .put("time", t.time).put("coin", t.coinId).put("side", t.side.name)
                                .put("amount", t.amount).put("price", t.price).put("usd", t.usd)
                                .put("fee", t.fee).put("pnl", t.pnl),
                        )
                    }
                },
            )
            .put(
                "events",
                JSONArray().also { a ->
                    state.events.forEach { e ->
                        a.put(
                            JSONObject()
                                .put("time", e.time).put("coin", e.coinId)
                                .put("kind", e.kind.name).put("percent", e.percent),
                        )
                    }
                },
            )
        return root.toString()
    }

    fun fromJson(text: String): GameState {
        val root = JSONObject(text)

        val customCoins = ArrayList<CustomCoin>()
        val customArray = root.optJSONArray("customCoins") ?: JSONArray()
        for (i in 0 until customArray.length()) {
            val o = customArray.getJSONObject(i)
            val style = runCatching { CoinStyle.valueOf(o.getString("style")) }.getOrNull() ?: continue
            val id = o.optString("id")
            if (id.isBlank() || Coins.get(id) != null || customCoins.any { it.id == id }) continue
            customCoins += CustomCoin(
                id = id,
                symbol = o.optString("symbol"),
                name = o.optString("name"),
                emoji = o.optString("emoji", "🪙"),
                color = o.optLong("color", 0xFF9E9E9E),
                style = style,
                serial = o.optInt("serial"),
                createdAt = o.optLong("createdAt"),
            )
        }
        val coinDefs = Coins.all + customCoins.map { it.toDef() }
        val coinIds = coinDefs.map { it.id }.toSet()

        val savedPrices = root.optJSONObject("prices") ?: JSONObject()
        val savedHistory = root.optJSONObject("history") ?: JSONObject()
        val prices = HashMap<String, Double>()
        val history = HashMap<String, List<Double>>()
        for (coin in coinDefs) {
            val price = savedPrices.optDouble(coin.id, coin.startPrice).takeIf { it.isFinite() && it > 0 }
                ?: coin.startPrice
            prices[coin.id] = price
            val points = readDoubles(savedHistory.optJSONArray(coin.id)).filter { it.isFinite() && it > 0 }
            history[coin.id] = points.ifEmpty { listOf(price) }.takeLast(PriceEngine.MAX_POINTS)
        }

        val holdings = HashMap<String, Holding>()
        val savedHoldings = root.optJSONObject("holdings") ?: JSONObject()
        for (key in savedHoldings.keys()) {
            if (key !in coinIds) continue
            val o = savedHoldings.getJSONObject(key)
            val amount = o.optDouble("amount", 0.0)
            if (amount > 0 && amount.isFinite()) holdings[key] = Holding(amount, o.optDouble("basis", 0.0))
        }

        val up = root.optJSONObject("upgrades") ?: JSONObject()
        val upgrades = Upgrades(
            tap = up.optInt("tap", 0).coerceAtLeast(0),
            auto = up.optInt("auto", 0).coerceAtLeast(0),
            crit = up.optInt("crit", 0).coerceIn(0, Clicker.MAX_CRIT_LEVEL),
            vault = up.optInt("vault", 0).coerceIn(0, Clicker.MAX_VAULT_LEVEL),
        )

        val trades = ArrayList<Trade>()
        val tradeArray = root.optJSONArray("trades") ?: JSONArray()
        for (i in 0 until tradeArray.length()) {
            val o = tradeArray.getJSONObject(i)
            val side = runCatching { TradeSide.valueOf(o.getString("side")) }.getOrNull() ?: continue
            val coin = o.optString("coin")
            if (coin !in coinIds) continue
            trades += Trade(
                o.optLong("time"), coin, side, o.optDouble("amount"), o.optDouble("price"),
                o.optDouble("usd"), o.optDouble("fee"), o.optDouble("pnl"),
            )
        }

        val events = ArrayList<MarketEvent>()
        val eventArray = root.optJSONArray("events") ?: JSONArray()
        for (i in 0 until eventArray.length()) {
            val o = eventArray.getJSONObject(i)
            val kind = runCatching { EventKind.valueOf(o.getString("kind")) }.getOrNull() ?: continue
            val coin = o.optString("coin")
            if (coin !in coinIds) continue
            events += MarketEvent(o.optLong("time"), coin, kind, o.optDouble("percent"))
        }

        val state = GameState(
            cash = root.optDouble("cash", 0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0,
            holdings = holdings,
            prices = prices,
            history = history,
            netHistory = readDoubles(root.optJSONArray("netHistory")).takeLast(PriceEngine.MAX_POINTS),
            sentiment = root.optDouble("sentiment", 0.0).takeIf { it.isFinite() } ?: 0.0,
            upgrades = upgrades,
            lifetimeEarned = root.optDouble("lifetimeEarned", 0.0).takeIf { it.isFinite() } ?: 0.0,
            taps = root.optLong("taps", 0),
            realizedPnl = root.optDouble("realizedPnl", 0.0).takeIf { it.isFinite() } ?: 0.0,
            feesPaid = root.optDouble("feesPaid", 0.0).takeIf { it.isFinite() } ?: 0.0,
            trades = trades,
            events = events,
            savedAt = root.optLong("savedAt", 0L),
            customCoins = customCoins,
            coinsListed = maxOf(root.optInt("coinsListed", 0), customCoins.maxOfOrNull { it.serial } ?: 0),
            granted = root.optDouble("granted", 0.0).takeIf { it.isFinite() } ?: 0.0,
        )
        return if (state.netHistory.isEmpty()) state.copy(netHistory = listOf(state.netWorth())) else state
    }

    private fun doubles(values: List<Double>): JSONArray = JSONArray().also { a -> values.forEach { a.put(it) } }

    private fun readDoubles(array: JSONArray?): List<Double> {
        if (array == null) return emptyList()
        return List(array.length()) { array.optDouble(it, Double.NaN) }.filter { !it.isNaN() }
    }
}
