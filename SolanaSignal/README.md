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
  `subscribeNewToken` / `subscribeTokenTrade` / `subscribeMigration` message shapes
  as best understood from the public docs (`https://pumpportal.fun/data-api/real-time/`).
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
