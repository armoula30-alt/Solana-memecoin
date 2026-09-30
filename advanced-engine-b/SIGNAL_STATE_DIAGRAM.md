# Advanced B Signal States

```mermaid
stateDiagram-v2
    [*] --> DISCOVERED
    DISCOVERED --> WATCH
    WATCH --> BUILDING
    BUILDING --> ACCELERATING
    ACCELERATING --> CONFIRMED
    CONFIRMED --> STRONG_SIGNAL
    STRONG_SIGNAL --> PEAKING
    PEAKING --> WEAKENING
    WEAKENING --> EXIT_RISK
    WATCH --> STALE
    BUILDING --> STALE
    ACCELERATING --> STALE
    CONFIRMED --> STALE
    STALE --> REMOVED
```

The current implementation requires repeated qualifying evaluations before `CONFIRMED` and `STRONG_SIGNAL`. A single abnormal tick can produce `EXIT_RISK` or `STALE`, but cannot promote a token directly to `STRONG_SIGNAL`.
