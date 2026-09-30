# Solana Signal — Advanced Engine B Architecture

```text
PumpPortal discovery + PumpDev trades
                 |
                 v
        LiveMarketState (one per mint)
          |       |       |       |
       Scanner  A/B     Paper   Charts/P&L/Outcome
                   |
          Baseline A | Advanced B
          unchanged  | experimental
```

`LiveMarketState` is an observation model, not a signal. It retains source, event timestamp, receive timestamp, latency, data age, freshness, and nullable features. `null` is UNKNOWN.

Advanced B components are pure/deterministic and independent:

- `MomentumEngineB`
- `BuyPressureEngineB`
- `LiquidityEngineB`
- `WalletIntelligenceEngineB`
- `MarketRegimeEngineB`
- `CollapseRiskEngineB`
- `PumpPotentialEngineB`
- `DataConfidenceEngineB`
- `SignalQualityEngineB`
- `DynamicRankerB`

`EngineABHarness` is an offline/replay contract. It passes the same state object to both consumers and does not replace the production SignalEngine.

Advanced B remains disabled from live signal decisions until replay and A/B validation are complete.
