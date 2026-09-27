package com.solanasignal.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.solanasignal.app.data.chart.ChartCandle
import com.solanasignal.app.ui.theme.BuyGreen
import com.solanasignal.app.ui.theme.SellRed
import com.solanasignal.app.ui.theme.TextMuted
import kotlin.math.max

@Composable
fun CandleChart(candles: List<ChartCandle>, modifier: Modifier = Modifier) {
    if (candles.isEmpty()) {
        Box(modifier.fillMaxWidth().height(240.dp).padding(16.dp)) {
            Text("Waiting for sufficient market data", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableFloatStateOf(0f) }
    val visibleCount = (candles.size / zoom).toInt().coerceIn(8, candles.size)
    val maxStart = (candles.size - visibleCount).coerceAtLeast(0)
    val start = (maxStart - pan.toInt()).coerceIn(0, maxStart)
    val visible = candles.subList(start, start + visibleCount)
    val minPrice = visible.minOf { it.low }
    val maxPrice = visible.maxOf { it.high }
    val range = (maxPrice - minPrice).takeIf { it > 0.0 } ?: maxPrice * 0.01

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    zoom = (zoom * zoomChange).coerceIn(1f, 8f)
                    pan = (pan - panChange.x / 16f).coerceIn(0f, maxStart.toFloat())
                }
            }
    ) {
        val chartTop = 10f
        val chartBottom = size.height - 10f
        val candleWidth = (size.width / visibleCount) * 0.68f
        val step = size.width / visibleCount
        fun y(price: Double): Float = (chartBottom - ((price - minPrice) / range * (chartBottom - chartTop))).toFloat()

        for (i in 0..4) {
            val gridY = chartTop + (chartBottom - chartTop) * i / 4f
            drawLine(TextMuted.copy(alpha = 0.18f), Offset(0f, gridY), Offset(size.width, gridY), 1f)
        }
        visible.forEachIndexed { index, candle ->
            val x = step * index + step / 2f
            val openY = y(candle.open)
            val closeY = y(candle.close)
            val highY = y(candle.high)
            val lowY = y(candle.low)
            val color = if (candle.close >= candle.open) BuyGreen else SellRed
            drawLine(color, Offset(x, highY), Offset(x, lowY), 2f)
            drawRect(
                color = color,
                topLeft = Offset(x - candleWidth / 2f, minOf(openY, closeY)),
                size = androidx.compose.ui.geometry.Size(candleWidth, max(2f, kotlin.math.abs(closeY - openY)))
            )
        }
        drawLine(TextMuted.copy(alpha = 0.5f), Offset(0f, chartBottom), Offset(size.width, chartBottom), 1f, cap = androidx.compose.ui.graphics.StrokeCap.Square)
    }
}
