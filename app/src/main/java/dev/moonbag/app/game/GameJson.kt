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
            .put("stakingEarned", state.stakingEarned)
            .put("startedAt", state.startedAt)
            .put("lastAirdropAt", state.lastAirdropAt)
            .put(
                "walletTxs",
                JSONArray().also { a ->
                    state.walletTxs.forEach { t ->
                        a.put(
                            JSONObject()
                                .put("time", t.time).put("kind", t.kind.name).put("coin", t.coinId)
                                .put("amount", t.amount).put("to", t.counterparty).put("toCoin", t.toCoinId)
                                .put("toAmount", t.toAmount).put("fee", t.feeUsd).put("value", t.valueUsd)
                                .put("chain", t.chainId),
                        )
                    }
                },
            )
            .put("nextOrderId", state.nextOrderId)
            .put(
                "orders",
                JSONArray().also { a ->
                    state.orders.forEach { o ->
                        a.put(
                            JSONObject()
                                .put("id", o.id).put("coin", o.coinId).put("type", o.type.name)
                                .put("trigger", o.trigger).put("amount", o.amount).put("createdAt", o.createdAt),
                        )
                    }
                },
            )
            .put(
                "orderFills",
                JSONArray().also { a ->
                    state.orderFills.forEach { f ->
                        a.put(
                            JSONObject()
                                .put("time", f.time).put("coin", f.coinId).put("type", f.type.name)
                                .put("price", f.price).put("amount", f.amount),
                        )
                    }
                },
            )
            .put(
                "campaigns",
                JSONArray().also { a ->
                    state.campaigns.forEach { c ->
                        a.put(
                            JSONObject()
                                .put("coin", c.coinId).put("kind", c.kind.name).put("strength", c.strength)
                                .put("left", c.ticksLeft).put("total", c.totalTicks),
                        )
                    }
                },
            )
            .put("nextNftId", state.nextNftId)
            .put(
                "activeEvent",
                state.activeEvent?.let {
                    JSONObject().put("kind", it.kind.name).put("left", it.ticksLeft).put("total", it.totalTicks)
                } ?: JSONObject.NULL,
            )
            .put("nftFloors", JSONObject().also { o -> state.nftFloors.forEach { (k, v) -> o.put(k, v) } })
            .put("nftHistory", JSONObject().also { o -> state.nftHistory.forEach { (k, v) -> o.put(k, doubles(v)) } })
            .put(
                "nfts",
                JSONArray().also { a ->
                    state.nfts.forEach { n ->
                        a.put(
                            JSONObject()
                                .put("id", n.id).put("collection", n.collection).put("seed", n.seed)
                                .put("rarity", n.rarity.name).put("mintedAt", n.mintedAt).put("paid", n.paid),
                        )
                    }
                },
            )
            .put(
                "customCoins",
                JSONArray().also { a ->
                    state.customCoins.forEach { c ->
                        a.put(
                            JSONObject()
                                .put("id", c.id).put("symbol", c.symbol).put("name", c.name)
                                .put("icon", c.icon).put("imageFile", c.imageFile ?: "").put("color", c.color).put("style", c.style.name)
                                .put("serial", c.serial).put("createdAt", c.createdAt).put("burned", c.burned),
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
                    .put("vault", state.upgrades.vault)
                    .put("farms", JSONArray().also { a -> state.upgrades.farms.forEach { a.put(it) } }),
            )
            .put("prices", JSONObject().also { o -> state.prices.forEach { (k, v) -> o.put(k, v) } })
            .put("history", JSONObject().also { o -> state.history.forEach { (k, v) -> o.put(k, doubles(v)) } })
            .put("netHistory", doubles(state.netHistory))
            .put(
                "holdings",
                JSONObject().also { o ->
                    state.holdings.forEach { (k, h) ->
                        o.put(k, JSONObject().put("amount", h.amount).put("basis", h.costBasis).put("staked", h.staked))
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
                icon = o.optString("icon", "star").ifBlank { "star" },
                color = o.optLong("color", 0xFF9E9E9E),
                style = style,
                serial = o.optInt("serial"),
                createdAt = o.optLong("createdAt"),
                imageFile = o.optString("imageFile", "").ifBlank { null },
                burned = o.optDouble("burned", 0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0,
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
            if (amount > 0 && amount.isFinite()) {
                val staked = o.optDouble("staked", 0.0).takeIf { it.isFinite() }?.coerceIn(0.0, amount) ?: 0.0
                holdings[key] = Holding(amount, o.optDouble("basis", 0.0), staked)
            }
        }

        val up = root.optJSONObject("upgrades") ?: JSONObject()
        val upgrades = Upgrades(
            tap = up.optInt("tap", 0).coerceAtLeast(0),
            auto = up.optInt("auto", 0).coerceAtLeast(0),
            crit = up.optInt("crit", 0).coerceIn(0, Clicker.MAX_CRIT_LEVEL),
            vault = up.optInt("vault", 0).coerceIn(0, Clicker.MAX_VAULT_LEVEL),
            farms = up.optJSONArray("farms").let { a ->
                List(Clicker.FARMS.size) { i -> (a?.optInt(i, 0) ?: 0).coerceAtLeast(0) }
            },
        )

        val nfts = ArrayList<Nft>()
        val nftArray = root.optJSONArray("nfts") ?: JSONArray()
        for (i in 0 until nftArray.length()) {
            val o = nftArray.getJSONObject(i)
            val rarity = runCatching { Rarity.valueOf(o.getString("rarity")) }.getOrNull() ?: continue
            val col = o.optString("collection")
            if (Nfts.collection(col) == null) continue
            nfts += Nft(o.optLong("id"), col, o.optInt("seed"), rarity, o.optLong("mintedAt"), o.optDouble("paid", 0.0))
        }
        val nftFloors = HashMap<String, Double>()
        val nftHistory = HashMap<String, List<Double>>()
        val savedFloors = root.optJSONObject("nftFloors") ?: JSONObject()
        val savedNftHistory = root.optJSONObject("nftHistory") ?: JSONObject()
        for (col in Nfts.collections) {
            val floor = savedFloors.optDouble(col.id, col.mintPrice).takeIf { it.isFinite() && it > 0 } ?: col.mintPrice
            nftFloors[col.id] = floor
            nftHistory[col.id] = readDoubles(savedNftHistory.optJSONArray(col.id))
                .filter { it.isFinite() && it > 0 }.ifEmpty { listOf(floor) }
        }
        val orders = ArrayList<Order>()
        val orderArray = root.optJSONArray("orders") ?: JSONArray()
        for (i in 0 until orderArray.length()) {
            val o = orderArray.getJSONObject(i)
            val type = runCatching { OrderType.valueOf(o.getString("type")) }.getOrNull() ?: continue
            val coin = o.optString("coin")
            val trigger = o.optDouble("trigger", 0.0)
            val amount = o.optDouble("amount", 0.0)
            if (coin !in coinIds || !trigger.isFinite() || trigger <= 0 || !amount.isFinite() || amount <= 0) continue
            orders += Order(o.optLong("id"), coin, type, trigger, amount, o.optLong("createdAt"))
        }
        val fills = ArrayList<OrderFill>()
        val fillArray = root.optJSONArray("orderFills") ?: JSONArray()
        for (i in 0 until fillArray.length()) {
            val o = fillArray.getJSONObject(i)
            val type = runCatching { OrderType.valueOf(o.getString("type")) }.getOrNull() ?: continue
            val coin = o.optString("coin")
            if (coin !in coinIds) continue
            fills += OrderFill(o.optLong("time"), coin, type, o.optDouble("price"), o.optDouble("amount"))
        }
        val campaigns = ArrayList<Campaign>()
        val campaignArray = root.optJSONArray("campaigns") ?: JSONArray()
        for (i in 0 until campaignArray.length()) {
            val o = campaignArray.getJSONObject(i)
            val kind = runCatching { CampaignKind.valueOf(o.getString("kind")) }.getOrNull() ?: continue
            val coin = o.optString("coin")
            val left = o.optDouble("left", 0.0)
            val total = o.optDouble("total", 0.0)
            if (coin !in customCoins.map { it.id } || !(left > 0) || !(total > 0)) continue
            campaigns += Campaign(coin, kind, o.optDouble("strength", 0.0).takeIf { it.isFinite() } ?: 0.0, left.coerceAtMost(total), total)
        }
        val event = root.optJSONObject("activeEvent")?.let { o ->
            val kind = runCatching { GlobalEventKind.valueOf(o.getString("kind")) }.getOrNull() ?: return@let null
            val total = o.optDouble("total", 0.0)
            val left = o.optDouble("left", 0.0)
            if (total > 0 && left > 0 && left.isFinite()) GlobalEvent(kind, left.coerceAtMost(total), total) else null
        }

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

        val walletTxs = ArrayList<WalletTx>()
        val txArray = root.optJSONArray("walletTxs") ?: JSONArray()
        for (i in 0 until txArray.length()) {
            val o = txArray.getJSONObject(i)
            val kind = runCatching { TxKind.valueOf(o.getString("kind")) }.getOrNull() ?: continue
            val coin = o.optString("coin")
            if (coin !in coinIds) continue
            val toCoin = o.optString("toCoin")
            walletTxs += WalletTx(
                time = o.optLong("time"), kind = kind, coinId = coin,
                amount = o.optDouble("amount", 0.0).takeIf { it.isFinite() } ?: 0.0,
                counterparty = o.optString("to"),
                toCoinId = if (toCoin in coinIds) toCoin else "",
                toAmount = o.optDouble("toAmount", 0.0).takeIf { it.isFinite() } ?: 0.0,
                feeUsd = o.optDouble("fee", 0.0).takeIf { it.isFinite() } ?: 0.0,
                valueUsd = o.optDouble("value", 0.0).takeIf { it.isFinite() } ?: 0.0,
                chainId = o.optString("chain"),
            )
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
            stakingEarned = root.optDouble("stakingEarned", 0.0).takeIf { it.isFinite() } ?: 0.0,
            activeEvent = event,
            nfts = nfts,
            nftFloors = nftFloors,
            nftHistory = nftHistory,
            orders = orders,
            nextOrderId = maxOf(root.optLong("nextOrderId", 1L), (orders.maxOfOrNull { it.id } ?: 0L) + 1),
            orderFills = fills,
            campaigns = campaigns,
            startedAt = root.optLong("startedAt", 0L).takeIf { it > 0 } ?: root.optLong("savedAt", 0L),
            walletTxs = walletTxs,
            lastAirdropAt = root.optLong("lastAirdropAt", 0L).coerceAtLeast(0L),
            nextNftId = maxOf(root.optLong("nextNftId", 1L), (nfts.maxOfOrNull { it.id } ?: 0L) + 1),
        )
        return if (state.netHistory.isEmpty()) state.copy(netHistory = listOf(state.netWorth())) else state
    }

    private fun doubles(values: List<Double>): JSONArray = JSONArray().also { a -> values.forEach { a.put(it) } }

    private fun readDoubles(array: JSONArray?): List<Double> {
        if (array == null) return emptyList()
        return List(array.length()) { array.optDouble(it, Double.NaN) }.filter { !it.isNaN() }
    }
}
