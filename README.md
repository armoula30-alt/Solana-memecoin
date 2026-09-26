# Solana Signal

Android (Kotlin + Jetpack Compose) real-time Solana memecoin **signal** app.

**Detect → Analyze → Score → Alert → Open Photon.**

This app does **not** hold a wallet, private key, or seed phrase, and it never signs
or broadcasts a Solana transaction. It only detects new tokens via PumpPortal,
computes metrics/safety/a momentum score, raises BUY/SELL alerts, and — when you
tap "OPEN IN PHOTON" — opens the official Photon web terminal so *you* can trade
manually. There is no automatic trading anywhere in this codebase.

---

## ⚠️ How to get the actual .apk

This project was generated as source code, not a compiled binary — building an APK
requires the Android SDK/Gradle toolchain, which isn't available in the environment
that generated it. To get the APK:

1. Install [Android Studio](https://developer.android.com/studio) (Hedgehog/2024+ or newer).
2. `File → Open` and select this project folder (the one with `settings.gradle.kts`).
3. Let Gradle sync (Android Studio will download the wrapper/Gradle 8.7 + AGP 8.5.2 automatically).
4. `Build → Build Bundle(s)/APK(s) → Build APK(s)`, or just press ▶ Run with a device/emulator attached.
5. For a release build you'll sign it with your own keystore (`Build → Generate Signed Bundle / APK`).

Minimum SDK 26 (Android 8.0), target/compile SDK 34.

---

## Before you flip the scanner on

1. Open the app → **Settings** → paste your PumpPortal API key (stored only in
   Android Keystore-backed `EncryptedSharedPreferences`; never logged, never sent
   anywhere except as part of the official PumpPortal WebSocket URL).
2. Or, leave the key blank and turn on **Mock Mode** in Settings to see the whole
   pipeline (fake tokens/trades/signals) without a live connection or spending any
   metered PumpPortal credits.
3. Go to **Dashboard** and flip the Scanner switch. Background scanning uses a
   foreground service with a persistent notification, per spec.

---

## Important implementation notes / honesty about limitations

- **PumpPortal field names**: `PumpPortalEventParser.kt` implements the documented
  `subscribeNewToken` event stream as best understood from the public docs
  (`https://pumpportal.fun/data-api/real-time/`).
  I did not have live network access while generating this project, so **before
  shipping, diff the field names in that parser against the current live docs** and
  adjust if PumpPortal has changed anything. The parser is written defensively
  (every field access is null-safe) specifically so a schema drift degrades
  gracefully (fields become `UNKNOWN`) instead of crashing.
- **Photon deep link**: there is no verified official token-specific deep-link URL
  format for Photon in the docs I could access. `PhotonLauncher.kt` therefore opens
  the official `https://photon-sol.tinyastro.io/` site and copies the token mint to
  the clipboard, per the spec's explicit fallback instructions. If Photon publishes
  (or you separately verify) a real deep-link format, update `buildOpenIntent()`.
- **Holder concentration / liquidity**: PumpPortal's real-time feed does not
  reliably expose these. They're wired as `UNKNOWN` end-to-end (DB, scoring, safety,
  UI) rather than faked — this is intentional per the spec's "never fabricate data"
  requirement, not an oversight. Wire in a real source later if you have one.
- **Signal Performance Analytics** (spec #37, hypothetical outcome tracking) has the
  data model (`SignalOutcomeEntity`/DAO) in place but the periodic price-recheck job
  that populates it isn't scheduled yet — straightforward to add as another
  WorkManager job that reads the latest price for open signals and calls
  `signalOutcomeDao().insert(...)`.
- **Market cap formula**: not independently derived here — the app uses whatever
  market-cap figure (if any) PumpPortal's payload provides; it does not compute one
  from supply × price because reliable circulating-supply data isn't guaranteed
  from the stream.

---

## Fix log

**v1.1** — fixed 4 real bugs found in testing:
1. **MC/Liq showing UNKNOWN** — the parser was reading field names that don't
   exist on PumpPortal's payload (`marketCapUsd`, `amountUsd`, etc.). Confirmed
   against working PumpPortal integrations that the real fields are SOL-denominated:
   `marketCapSol`, `vSolInBondingCurve`, `vTokensInBondingCurve`, `solAmount`,
   `tokenAmount`. Added `SolPriceProvider` (polls Binance's public SOL/USDT ticker
   every 60s, no key needed) and convert SOL → USD in `ScannerOrchestrator`. MC/Liq
   will briefly show UNKNOWN for the first few seconds after starting the scanner
   (live mode) until the first price fetch completes — this is shown on Dashboard.
2. **Liquidity was never populated at all** — it's now derived from
   `vSolInBondingCurve` (the SOL reserve in the bonding curve), a standard proxy
   for pre-migration liquidity.
3. **B/S always 0/0** — the Scanner screen was reading buyer/seller counts off the
   *last emitted signal*, which often didn't exist yet or was stale (cooldown/dedupe
   intentionally suppresses repeat WATCH/REJECTED signals). Buyer/seller/volume
   numbers are now cached straight onto the token row on every trade and read live.
4. **Photon link always opened /discover (homepage)** — confirmed Photon's real
   token page format is `photon-sol.tinyastro.io/en/lp/{poolAddress}`, keyed by
   the liquidity-pool address, not the mint. `PhotonLauncher` now uses PumpPortal's
   `bondingCurveKey` as that pool address when available. Caveat: this only reliably
   works pre-migration — after a token migrates to Raydium/PumpSwap its real LP
   address differs from the pump.fun bonding curve, so post-migration tokens may
   still fall back to the homepage (with the mint copied to clipboard) until a
   verified post-migration pool-address field is confirmed.

---

## DexScreener integration (v1.2)

Added `DexScreenerClient` (`data/dexscreener/`) as a second, free, keyless data
source layered on top of PumpPortal:

- **What it fixes**: PumpPortal only reports SOL-denominated figures, so MC/Liquidity
  in the app were always our own SOL→USD conversion (via SolPriceProvider). Now,
  every ~20 seconds, `ScannerOrchestrator` batch-queries
  `https://api.dexscreener.com/latest/dex/tokens/{mint1},{mint2},...` (up to 30
  mints/call, no API key) for every currently-tracked token and overwrites
  `marketCapUsd` / `liquidityUsd` / `lastPriceUsd` with DexScreener's own authoritative
  numbers once a token is indexed there.
- **Pool address / Photon link**: DexScreener's `pairAddress` is the token's real
  on-chain LP address - and unlike PumpPortal's `bondingCurveKey` (pre-migration
  only), DexScreener tracks Raydium/PumpSwap pools too. So `TokenEntity.poolAddress`
  now prefers DexScreener's value once available, which means the Photon deep link
  (`photon-sol.tinyastro.io/en/lp/{poolAddress}`) keeps working correctly **after** a
  token migrates - closing the gap called out in the v1.1 fix log.
- **DEX shown per token**: `dexId` ("pumpfun", "raydium", "pumpswap"...) is now shown
  on the Token Detail screen so you can see whether a token is still pre-migration
  (bonding curve, not yet DexScreener-indexed) or trading on a real DEX.
- **Rate limiting**: DexScreener doesn't publish a hard documented limit; independent
  integrations report treating ~300 req/min as a safe ceiling. Polling every 20s in
  batches of 30 stays far under that even at the max tracked-token count (150 in
  Performance battery mode = 5 requests/poll = 15 req/min).
- **Brand-new tokens**: a token that's seconds old usually isn't indexed by
  DexScreener yet - it just keeps showing the PumpPortal/SOL-derived estimate until
  the next enrichment pass picks it up (typically under a minute).
- **Mock Mode**: enrichment is skipped entirely in Mock Mode (nothing real to look up).
- Check **System Status** for a live "DexScreener enriched (last pass)" counter to
  confirm it's working.

## Discovery and enrichment pipeline (v1.4)

The app now uses a deliberate two-source pipeline:

1. **PumpPortal WebSocket is the discovery source only.** `subscribeNewToken`
   detects new launches immediately. The app does not subscribe to PumpPortal
   token trades; this avoids metered trade subscriptions and keeps PumpPortal
   focused on finding new mints.
2. **DexScreener is the analysis and enrichment source.** Every tracked mint is queried in
   batches (up to 30 addresses per request) shortly after discovery and then on a
   rolling interval. The app stores the best-liquidity known pair and its DEX,
   pair URL, price, liquidity, market cap, FDV, volumes, buy/sell counts, price
   changes, boosts, image, description, websites, and socials when available.
3. **Fallback is explicit.** A token can be visible and scored from PumpPortal
   before DexScreener indexes it. The UI marks those fields as not indexed rather
   than fabricating values; later enrichment updates the same token row.

This improves early detection and context, but it is not a guarantee that a token will pump. DexScreener is eventually consistent, can lag on brand-new tokens, and its API terms and rate limits apply. PumpPortal token-trade subscriptions are also metered according to its current documentation. The app remains signal-only: it does not hold a wallet, sign, or submit a transaction.

Optional CodeCraft AI review is available in Settings. The app sends only locally filtered, high-scoring DexScreener candidates to `https://codecraftapi.com/v1/chat/completions`, using the configured model and an encrypted on-device API key. The response is constrained to JSON (`BUY_CANDIDATE`, `WATCH`, or `REJECTED`, confidence, risk, reasons, and red flags) and is advisory only; it cannot execute trades. AI review is rate-limited per token and skipped when no CodeCraft key is configured.

---

## "Sniping" - what this app does and does not do (v1.3)

A user asked for "sniping." Clarified scope: this app detects pump momentum and
gets you to the token in Photon as fast as possible - it does **not** hold a
wallet or auto-execute buys. True automated sniping (a bot holding a hot wallet
that signs and submits a buy transaction the instant a token is created, racing
other bots on priority fees / Jito bundles) is a fundamentally different, much
higher-risk product: it means the app custodies funds, and it buys before any
safety signal (liquidity, holder distribution, creator behavior) even exists -
maximum rug-pull exposure. That's intentionally out of scope here.

What v1.3 actually fixes, matching the "detect pump -> jump straight to the coin
in Photon, no auto-trading" ask:
- **Notification tap now goes directly to Photon** (`setContentIntent` on the
  BUY/SELL notification points at the same intent as the "OPEN PHOTON" action).
  Previously it just opened the app to the Dashboard with no useful navigation -
  a real gap, now fixed.
- **DETAILS is the secondary action** - opens the in-app token screen (reasons,
  metrics, safety checks) if you want to check before opening Photon.
- **Fixed a related bug**: the tap intent was carrying an unused `signalId` extra
  that `MainActivity` never read, so tapping the notification never navigated
  anywhere specific even in-app. `MainActivity` now handles the deep link on both
  cold start and while already open (`onNewIntent`, since it's `singleTop`).

## Architecture

```
PumpPortalWebSocketManager (data/pumpportal)
        -> PumpPortalEventParser -> Normalized*Event
        -> ScannerOrchestrator (domain/scanner)
               -> MetricsEngine (domain/metrics)      rolling 30s/1m/3m/5m windows
               -> SafetyEngine (domain/safety)         PASS/WARN/FAIL/UNKNOWN checks
               -> ScoringEngine (domain/scoring)       explainable 0-100 score
               -> SignalEngine (domain/signals)        hard filters + cooldown/dedupe
        -> Room (data/room)                            tokens/trades/metrics/scores/signals
        -> NotificationHelper                          BUY/SELL/SAFETY/SYSTEM channels
        -> PhotonLauncher                               opens Photon, never trades
UI: Jetpack Compose, 5 tabs (Dashboard, Scanner, History, Status, Settings)
```

No backend/VPS/cloud component — everything runs on-device (Phase 1, per spec).
