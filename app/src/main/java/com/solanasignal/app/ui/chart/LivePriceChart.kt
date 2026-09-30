package com.solanasignal.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.solanasignal.app.data.market.MarketPoint
import com.solanasignal.app.ui.theme.BuyGreen
import com.solanasignal.app.ui.theme.NeutralBlue
import com.solanasignal.app.ui.theme.SellRed
import com.solanasignal.app.ui.theme.TextMuted
import kotlin.math.abs
import kotlin.math.max

data class ChartMarker(val timestampMs: Long, val priceUsd: Double?, val kind: Kind) {
    enum class Kind { PAPER_BUY, PAPER_SELL, SIGNAL }
}

/** Draws only retained real price observations/trades; no synthetic candles or interpolated ticks. */
@Composable
fun LivePriceChart(points: List<MarketPoint>, markers: List<ChartMarker>, modifier: Modifier = Modifier) {
    val values = points.mapNotNull { point -> point.priceUsd?.takeIf { it.isFinite() && it > 0.0 }?.let { point to it } }
        .sortedBy { it.first.timestampMs }
    if (values.size < 2) {
        Text("Waiting for at least two actual price observations", color = TextMuted, modifier = modifier)
        return
    }
    val minTime = values.first().first.timestampMs.toDouble()
    val maxTime = values.last().first.timestampMs.toDouble()
    val minPrice = values.minOf { it.second }
    val maxPrice = values.maxOf { it.second }
    val priceRange = (maxPrice - minPrice).takeIf { it > 0.0 } ?: max(abs(maxPrice) * 0.01, 1e-12)
    val timeRange = (maxTime - minTime).takeIf { it > 0.0 } ?: 1.0

    Canvas(modifier = modifier.fillMaxWidth().height(190.dp)) {
        val left = 5f
        val right = size.width - 5f
        val top = 8f
        val bottom = size.height - 8f
        repeat(4) { index ->
            val y = top + (bottom - top) * index / 3f
            drawLine(TextMuted.copy(alpha = 0.20f), Offset(left, y), Offset(right, y), 1f)
        }
        val coordinates = values.map { (point, price) ->
            val x = left + ((point.timestampMs - minTime) / timeRange * (right - left)).toFloat()
            val y = bottom - ((price - minPrice) / priceRange * (bottom - top)).toFloat()
            point to Offset(x, y)
        }
        val path = Path().apply {
            coordinates.forEachIndexed { index, entry ->
                if (index == 0) moveTo(entry.second.x, entry.second.y) else lineTo(entry.second.x, entry.second.y)
            }
        }
        val rising = values.last().second >= values.first().second
        drawPath(path, if (rising) BuyGreen else SellRed, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        coordinates.forEach { (point, offset) ->
            val color = when (point.side?.name) {
                "BUY" -> BuyGreen
                "SELL" -> SellRed
                else -> NeutralBlue
            }
            drawCircle(color, radius = 2.5f, center = offset)
        }
        markers.forEach { marker ->
            val nearest = values.minByOrNull { abs(it.first.timestampMs - marker.timestampMs) } ?: return@forEach
            val x = left + ((nearest.first.timestampMs - minTime) / timeRange * (right - left)).toFloat()
            val markerPrice = marker.priceUsd?.takeIf { it > 0.0 } ?: nearest.second
            val y = bottom - ((markerPrice - minPrice) / priceRange * (bottom - top)).toFloat()
            val color = when (marker.kind) {
                ChartMarker.Kind.PAPER_BUY -> BuyGreen
                ChartMarker.Kind.PAPER_SELL -> SellRed
                ChartMarker.Kind.SIGNAL -> NeutralBlue
            }
            drawCircle(color, radius = 6f, center = Offset(x, y))
            drawCircle(Color.White, radius = 6f, center = Offset(x, y), style = Stroke(width = 1.5f))
        }
    }
}
