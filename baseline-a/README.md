# Baseline A — Golden Reference

## Preservation status

Baseline A is the existing Solana Signal implementation frozen at commit `62e13074ad7cc4ce12a25816c37d75fb8cd4e651` before Advanced Engine B. The production Baseline A classes remain in their original packages and are not rewritten or disabled:

- `app/src/main/java/com/solanasignal/app/domain/signals/SignalEngine.kt`
- `app/src/main/java/com/solanasignal/app/domain/scoring/ScoringEngine.kt`
- `app/src/main/java/com/solanasignal/app/domain/momentum/MomentumEngine.kt`
- `app/src/main/java/com/solanasignal/app/domain/safety/SafetyEngine.kt`
- `app/src/main/java/com/solanasignal/app/domain/scanner/ScannerOrchestrator.kt`

Advanced Engine B is not enabled in the live decision path until its tests and replay/A-B harness are complete.

## Existing behavior that is preserved

The current engine evaluates hard filters, score thresholds, safety checks, cooldown/deduplication, and BUY/SELL/WATCH/REJECTED classifications. Missing values remain unavailable rather than being fabricated. Existing runtime data continues to use PumpPortal discovery, PumpDev trades, normalized events, MetricsEngine, SafetyEngine, ScoringEngine, and SignalEngine.

## APK-recovered function inventory

The following names were requested as recovered APK behavior. No exact implementation or decompiled source for these functions exists in the current repository, so no formula is invented for Baseline A.

| Recovered behavior | Status | Evidence in current repository |
|---|---|---|
| `evaluateMarketQuality` | UNKNOWN | No exact function or verified formula found |
| `scoreIntelligence` | UNKNOWN | No exact function or verified formula found |
| `momentumPct` | UNKNOWN | No exact function or verified formula found |
| `probabilityUp` | UNKNOWN | No exact function or verified formula found |
| `probabilityDown` | UNKNOWN | No exact function or verified formula found |
| `persistent_buy_side_dominance` | UNKNOWN | No exact APK implementation found; current engines expose related buy-pressure concepts only |
| `classifySignalOutcome` | UNKNOWN | No exact APK implementation found; current outcome records are hypothetical observations |
| `first_signal_price_usd` | UNKNOWN | No exact APK implementation found |
| `current_multiple` | UNKNOWN | No exact APK implementation found |
| `max_multiple` | UNKNOWN | No exact APK implementation found |
| `drawdown_pct` | UNKNOWN | No exact APK implementation found |
| `peak_change_pct` | UNKNOWN | No exact APK implementation found |

`UNKNOWN` means the behavior is intentionally not reconstructed. It must not be used as a hidden formula or as evidence that Baseline A has a particular APK behavior.

## Baseline A safety rule

Do not claim that Advanced Engine B improves win rate, profitability, drawdown, or false-positive rate until both engines have processed identical captured events and the same outcome methodology has produced comparable A/B metrics.
