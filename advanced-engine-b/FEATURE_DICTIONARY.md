# Advanced Engine B Feature Dictionary

| Feature | Meaning | Required observations | Missing-data behavior |
|---|---|---|---|
| `priceVelocity` | Observed price change normalized to a minute | At least two timestamped prices | UNKNOWN |
| `priceAcceleration` | Change in observed price velocity | Comparable windows | UNKNOWN |
| `marketCapVelocity` | Observed market-cap movement | Timestamped market-cap values | UNKNOWN |
| `volumeVelocity` | Recent observed volume relative to a comparable window | Volume windows | UNKNOWN |
| `buyVolume` / `sellVolume` | Observed quote volume by side | Trade amounts and side | UNKNOWN |
| `persistent_buy_side_dominance` | Buy-side dominance across multiple windows | At least two windows with side volumes | UNKNOWN |
| `liquidityToMarketCap` | Liquidity divided by observed market cap | Both fields | UNKNOWN |
| `uniqueBuyers` / `uniqueSellers` | Distinct provider-supplied traders | Trader identities | UNKNOWN |
| `walletConcentration` | Holder/trader concentration | Provider concentration data | UNKNOWN |
| `collapseRisk` | Independent reversal/liquidity/activity risk | At least one risk input | UNKNOWN |
| `pumpPotential` | Ranking feature, not profit probability | Momentum/pressure/quality inputs | UNKNOWN |
| `dataConfidence` | Coverage adjusted for freshness | Field coverage and timing | Numeric coverage score with missing-field reasons |
| `signalQuality` | Combined research quality | Pump potential, risk, confidence | UNKNOWN when no inputs |

No feature is populated from stale REST movement or assumed zeros. The current PumpDev stream does not reliably provide holder concentration, deployer behavior, or wallet clustering; those remain UNKNOWN.
