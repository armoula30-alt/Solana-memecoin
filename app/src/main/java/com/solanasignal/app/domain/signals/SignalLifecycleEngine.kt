package com.solanasignal.app.domain.signals

import com.solanasignal.app.domain.manipulation.ManipulationRiskLevel
import com.solanasignal.app.domain.momentum.MomentumState


enum class SignalLifecycleState {
    DISCOVERED, WATCH, EARLY_MOMENTUM, STRONG_MOMENTUM, EXTREME_MOMENTUM,
    WEAKENING, INVALIDATED, AVOID, INSUFFICIENT_DATA, EXPIRED
}

data class LifecycleTransition(
    val previous: SignalLifecycleState?,
    val current: SignalLifecycleState,
    val changed: Boolean,
    val reason: String
)

/** Enforces meaningful signal transitions; it does not jump randomly between states. */
class SignalLifecycleEngine {
    fun transition(
        previous: SignalLifecycleState?,
        momentum: MomentumState,
        score: Int,
        manipulation: ManipulationRiskLevel,
        safetyFailed: Boolean,
        dataQualityScore: Int?
    ): LifecycleTransition {
        val candidate = when {
            safetyFailed || manipulation == ManipulationRiskLevel.CRITICAL -> SignalLifecycleState.AVOID
            dataQualityScore != null && dataQualityScore < 30 -> SignalLifecycleState.INSUFFICIENT_DATA
            momentum == MomentumState.COLLAPSING -> SignalLifecycleState.INVALIDATED
            momentum == MomentumState.WEAKENING -> SignalLifecycleState.WEAKENING
            momentum == MomentumState.EXTREME -> SignalLifecycleState.EXTREME_MOMENTUM
            momentum == MomentumState.STRONG -> SignalLifecycleState.STRONG_MOMENTUM
            momentum == MomentumState.EARLY -> SignalLifecycleState.EARLY_MOMENTUM
            score >= 45 -> SignalLifecycleState.WATCH
            else -> SignalLifecycleState.DISCOVERED
        }
        if (previous == null) return LifecycleTransition(null, candidate, true, "Initial lifecycle classification")
        if (previous == candidate) return LifecycleTransition(previous, previous, false, "No lifecycle change")
        if (!allowed(previous, candidate)) {
            return LifecycleTransition(previous, previous, false, "Transition $previous -> $candidate is not allowed without an intermediate state")
        }
        return LifecycleTransition(previous, candidate, true, "Momentum=${momentum.name}, score=$score, manipulation=${manipulation.name}")
    }

    private fun allowed(from: SignalLifecycleState, to: SignalLifecycleState): Boolean = when (from) {
        SignalLifecycleState.DISCOVERED -> to in setOf(SignalLifecycleState.WATCH, SignalLifecycleState.AVOID, SignalLifecycleState.INSUFFICIENT_DATA)
        SignalLifecycleState.WATCH -> to in setOf(SignalLifecycleState.EARLY_MOMENTUM, SignalLifecycleState.AVOID, SignalLifecycleState.INSUFFICIENT_DATA)
        SignalLifecycleState.EARLY_MOMENTUM -> to in setOf(SignalLifecycleState.STRONG_MOMENTUM, SignalLifecycleState.WEAKENING, SignalLifecycleState.AVOID, SignalLifecycleState.INSUFFICIENT_DATA)
        SignalLifecycleState.STRONG_MOMENTUM -> to in setOf(SignalLifecycleState.EXTREME_MOMENTUM, SignalLifecycleState.WEAKENING, SignalLifecycleState.INVALIDATED, SignalLifecycleState.AVOID)
        SignalLifecycleState.EXTREME_MOMENTUM -> to in setOf(SignalLifecycleState.WEAKENING, SignalLifecycleState.INVALIDATED, SignalLifecycleState.AVOID)
        SignalLifecycleState.WEAKENING -> to in setOf(SignalLifecycleState.INVALIDATED, SignalLifecycleState.AVOID)
        SignalLifecycleState.INVALIDATED -> to in setOf(SignalLifecycleState.WATCH, SignalLifecycleState.AVOID, SignalLifecycleState.EXPIRED)
        SignalLifecycleState.INSUFFICIENT_DATA -> to in setOf(SignalLifecycleState.WATCH, SignalLifecycleState.AVOID)
        SignalLifecycleState.AVOID -> to in setOf(SignalLifecycleState.EXPIRED)
        SignalLifecycleState.EXPIRED -> false
    }
}
