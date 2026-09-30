# Outcome Dataset Schema

The existing Room records remain the source of truth for observations, feature snapshots, signals, and hypothetical outcomes. Advanced B snapshots should be stored with the following logical fields:

- `mint`, `signalTimestamp`, `engine` (`A` or `B`)
- all available signal-time features
- `dataConfidence`, `pumpPotential`, `collapseRisk`, `signalQuality`
- `horizonSeconds`, `observedPrice`, `returnPct`
- `maxGainPct`, `maxDrawdownPct`, `timeToPeakSeconds`
- `timeTo25PctSeconds`, `timeTo50PctSeconds`, `timeTo100PctSeconds`
- `failureTimeSeconds`, `outcomeClassification`

Only information available at `signalTimestamp` belongs in the feature snapshot. Future observations belong exclusively in outcome fields.

These are hypothetical research outcomes, not executed trades and not guarantees of profit.
