# A/B Validation Dashboard Contract

Baseline A and Advanced B must receive the same `LiveMarketState` sequence and the same signal-time snapshot. No future fields may be included in either decision.

Required comparison metrics:

- signal count
- profitable signal count
- loss count
- win rate
- median and mean return
- median maximum gain
- median drawdown
- false-positive rate
- signal frequency
- average time to signal
- data coverage

Outcome horizons:

`30s, 1m, 2m, 5m, 10m, 15m, 30m, 1h, 6h, 24h`

The existing walk-forward engine provides a no-future-leakage foundation. A/B improvement is **not claimed** until both engines have captured identical events and the reports contain enough resolved observations.
