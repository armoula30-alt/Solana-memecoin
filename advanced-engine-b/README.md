# Advanced Engine B — Experimental

Advanced Engine B is an independent, deterministic, non-ML research engine. It consumes the shared `LiveMarketState` model and produces separate momentum, buy-pressure, liquidity, wallet, collapse-risk, pump-potential, confidence, signal-quality, state-machine, and ranking outputs.

## Activation gate

Engine B is intentionally not enabled as a replacement for Baseline A. It must first pass:

1. unit tests for every component;
2. integration tests from live events to state to engine outputs;
3. replay tests with captured events;
4. reconnect, duplicate, out-of-order, missing-field, stale-data, and provider-failure tests;
5. identical-data A/B outcome comparison.

## Data policy

- `null` means UNKNOWN / unavailable; it is never silently converted to zero.
- Motion is calculated only from real observations with event timestamps.
- Stale data is marked and cannot produce a strong signal.
- No polling result is used to fabricate movement between events.
- No AI/ML is used in the live decision path.
- Engine B is a ranking/research signal, not a prediction or guarantee.

## Current implementation boundary

The foundational shared state and pure Engine B components are added first. Existing Baseline A signal/scoring/metrics/paper implementations remain untouched. Production enablement is deferred until the validation gate is complete.

## Unavailable data

The current providers do not reliably supply holder concentration, deployer behavior, wallet clustering, slippage impact, or complete liquidity history for every token. Engine B returns UNKNOWN for those values and records reason codes instead of estimating them.
