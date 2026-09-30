package com.solanasignal.app.domain.replay

import com.solanasignal.app.data.room.entities.MarketEventEntity
import com.solanasignal.app.domain.ab.EngineABHarness
import com.solanasignal.app.domain.live.LiveMarketState

data class ReplayPoint<A>(val event: MarketEventEntity, val result: com.solanasignal.app.domain.ab.EngineABResult<A>)
data class ReplayResult<A>(val points: List<ReplayPoint<A>>, val normalisedEvents: Int, val skippedEvents: Int)

/** Replays normalized events in original event order; future events are never visible. */
class HistoricalReplayEngine<A>(
    private val harness: EngineABHarness<A>,
    private val stateBuilder: (previous: LiveMarketState?, event: MarketEventEntity) -> LiveMarketState
) {
    fun replay(events: List<MarketEventEntity>, nowAtEvent: (MarketEventEntity) -> Long = { it.receiveTimestamp }): ReplayResult<A> {
        var previous: LiveMarketState? = null
        var lastTimestamp = Long.MIN_VALUE
        val points = mutableListOf<ReplayPoint<A>>()
        var skipped = 0
        events.forEach { event ->
            if (event.timestamp < lastTimestamp) {
                skipped++
                return@forEach
            }
            val state = stateBuilder(previous, event)
            val result = harness.evaluate(state, nowAtEvent(event))
            points += ReplayPoint(event, result)
            previous = state
            lastTimestamp = event.timestamp
        }
        return ReplayResult(points, points.size, skipped)
    }
}
