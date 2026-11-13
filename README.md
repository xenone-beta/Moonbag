# Moonbag

An offline meme-coin trading **game** for Android. Every coin is made up, every price is simulated on
your phone, and all money is play money. No internet permission, no accounts, no real crypto.

## What you can do

- **Market**: 10 major coins (BTC, ETH, SOL and others) and 12 invented meme coins with live charts,
  filters and market news (pumps, dumps and the occasional rug pull). All prices are simulated; the major
  coins only borrow real names and are not market data.
- **Create your own coin**: pay a $2,000 listing fee, pick a name, ticker, icon, colour and behaviour, and get a
  numbered listing certificate plus a founder allocation. Your coins live on a thin market: your own trades
  move the price (a constant-product pool), so a quick buy-and-sell always costs you.
- **Trade**: buy and sell with a 0.5% fee, see your average buy price and profit per coin.
- **Portfolio**: net worth chart, allocation bar, holdings and trade history.
- **Mine**: you start with $0. Tap the moon to earn your first dollars, then buy upgrades
  (stronger taps, an auto-miner, lucky crits, an offline vault).
- **Offline**: the market keeps moving while the app is closed, and the auto-miner keeps earning for as
  long as the offline vault allows.
- Material 3 with dynamic colors (Material You), light and dark theme, English and Russian.

## How the market works

Each coin follows a mean-reverting random walk (Ornstein-Uhlenbeck on the log price) with its own
volatility, shifted by a shared market mood, plus rare pumps, dumps and (for risky coins) rug pulls.
Because the discretisation is exact, a few big steps equal many small ones, which is how the time away is
simulated. See `game/PriceEngine.kt`.

## Build

```
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Requires JDK 17. GitHub Actions builds a debug APK on every push (`.github/workflows/ci.yml`) and a
release APK when you push a tag like `v1.0.0` (`release.yml`).

### Signing release builds

Add these repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Without them the release APK is signed with the debug key, so it still installs.
Never commit the `.jks` file.

## Project layout

```
game/   coins, price engine, trading, clicker, save format (pure Kotlin, unit tested)
ui/     Jetpack Compose screens: market, portfolio, mine, settings
```

## License

MIT
