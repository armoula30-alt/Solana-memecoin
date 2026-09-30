package com.solanasignal.app.ui.paper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.solanasignal.app.data.market.LiveMarketState
import com.solanasignal.app.data.market.MarketDataSource
import com.solanasignal.app.data.market.MarketDataStatus
import com.solanasignal.app.data.room.entities.PaperPositionEntity
import com.solanasignal.app.data.room.entities.PaperTradeEntity
import com.solanasignal.app.domain.paper.PaperTradeResult
import com.solanasignal.app.ui.AppViewModel
import com.solanasignal.app.ui.chart.ChartMarker
import com.solanasignal.app.ui.chart.LivePriceChart
import com.solanasignal.app.ui.theme.BgDark
import com.solanasignal.app.ui.theme.BuyGreen
import com.solanasignal.app.ui.theme.NeutralBlue
import com.solanasignal.app.ui.theme.SellRed
import com.solanasignal.app.ui.theme.SurfaceDark
import com.solanasignal.app.ui.theme.SurfaceRaised
import com.solanasignal.app.ui.theme.TextMuted
import com.solanasignal.app.ui.theme.WatchAmber
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun PaperTerminalScreen(vm: AppViewModel) {
    val ranked by vm.rankedTokens.collectAsState()
    val marketStates by vm.liveMarketStates.collectAsState()
    val portfolio by vm.paperPortfolio.collectAsState()
    val positions by vm.paperPositions.collectAsState()
    val trades by vm.paperTrades.collectAsState()
    val signals by vm.signals.collectAsState()
    val analytics by vm.paperAnalytics.collectAsState()
    val autoStatus by vm.autoPaperStatus.collectAsState()

    var selectedMint by remember { mutableStateOf<String?>(null) }
    var buyAmountText by remember { mutableStateOf("25") }
    var presetAmount by remember { mutableStateOf(25) }
    var sellFraction by remember { mutableFloatStateOf(1f) }
    var timeframeSeconds by remember { mutableIntStateOf(60) }
    var actionStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ranked, positions) {
        val validMints = ranked.map { it.token.mint }.toSet() + positions.map { it.mint }
        if (selectedMint !in validMints) selectedMint = positions.firstOrNull()?.mint ?: ranked.firstOrNull()?.token?.mint
    }

    val mint = selectedMint
    val selectedRank = ranked.firstOrNull { it.token.mint == mint }
    val selectedToken = selectedRank?.token
    val market = mint?.let { marketStates[it] } ?: LiveMarketState(
        mint = mint ?: "",
        symbol = selectedToken?.symbol,
        name = selectedToken?.name
    )
    val position = positions.firstOrNull { it.mint == mint }
    val livePrice = market.priceUsd
    val canTrade = mint != null && vm.canPaperTrade(mint)
    val points = market.points.filter { it.priceUsd != null }.sortedBy { it.timestampMs }
    val spanMs = if (points.size >= 2) points.last().timestampMs - points.first().timestampMs else 0L
    val availableTimeframes = listOf(5, 15, 60, 300).filter { spanMs >= it * 1000L && points.count { point -> point.timestampMs >= points.last().timestampMs - it * 1000L } >= 2 }
    LaunchedEffect(availableTimeframes) {
        if (timeframeSeconds !in availableTimeframes) timeframeSeconds = availableTimeframes.lastOrNull() ?: 0
    }
    val visiblePoints = if (timeframeSeconds > 0 && points.isNotEmpty()) {
        val cutoff = points.last().timestampMs - timeframeSeconds * 1000L
        points.filter { it.timestampMs >= cutoff }
    } else points
    val cutoffForMarkers = visiblePoints.firstOrNull()?.timestampMs ?: 0L
    val markers = buildList {
        trades.asSequence().filter { it.mint == mint && it.timestamp >= cutoffForMarkers }.forEach { trade ->
            add(ChartMarker(trade.timestamp, trade.fillPriceUsd, if (trade.side == "PAPER_BUY") ChartMarker.Kind.PAPER_BUY else ChartMarker.Kind.PAPER_SELL))
        }
        signals.asSequence().filter { it.mint == mint && it.timestamp >= cutoffForMarkers && it.priceUsd != null }.forEach { signal ->
            add(ChartMarker(signal.timestamp, signal.priceUsd, ChartMarker.Kind.SIGNAL))
        }
    }
    val selectedSignal = signals.firstOrNull { it.mint == mint && it.signalType != "REJECTED" }
    val buyAmount = buyAmountText.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
    val liquidity = market.liquidityUsd?.takeIf { it > 0.0 }
    val buySlip = if (canTrade) estimateSlippage(buyAmount, liquidity) else 0.0
    val feeRate = 0.003
    val estimatedBuyFee = buyAmount * feeRate
    val estimatedBuyExecution = livePrice?.takeIf { canTrade }?.let { it * (1.0 + buySlip) }
    val estimatedBuyQuantity = if (estimatedBuyExecution != null && estimatedBuyExecution > 0.0) (buyAmount - estimatedBuyFee).coerceAtLeast(0.0) / estimatedBuyExecution else null
    val sellQuantity = (position?.quantity ?: 0.0) * sellFraction
    val estimatedSellSlip = if (canTrade) estimateSlippage((livePrice ?: 0.0) * sellQuantity, liquidity) else 0.0
    val estimatedSellExecution = livePrice?.takeIf { canTrade }?.let { it * (1.0 - estimatedSellSlip) }
    val estimatedSellFee = estimatedSellExecution?.let { it * sellQuantity * feeRate }
    val estimatedSellReceived = estimatedSellExecution?.let { it * sellQuantity - (estimatedSellFee ?: 0.0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = BgDark), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("SOLANA SIGNAL  /  PAPER TERMINAL", color = NeutralBlue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(market.symbol ?: selectedToken?.symbol ?: mint?.take(10) ?: "Select token", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                            Text(market.name ?: selectedToken?.name ?: "REAL MARKET DATA  •  SIMULATED EXECUTION", color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        StatusPill(market.status, market.priceSource)
                    }
                    Text(mint ?: "Select a tracked token or open position", color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(livePrice?.let(::formatPrice) ?: "PRICE UNKNOWN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = priceColor(market, livePrice))
                        Text("${market.priceUpdatedAtMs?.let { relativeAge(System.currentTimeMillis() - it) } ?: "no quote"}  •  ${market.priceSource.name}", color = TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ranked.take(30).forEach { candidate ->
                    FilterChip(
                        selected = candidate.token.mint == mint,
                        onClick = { selectedMint = candidate.token.mint; actionStatus = null },
                        label = { Text("${candidate.token.symbol ?: candidate.token.mint.take(5)} · ${candidate.market.status.name}") }
                    )
                }
                positions.filter { p -> ranked.none { it.token.mint == p.mint } }.forEach { p ->
                    FilterChip(selected = p.mint == mint, onClick = { selectedMint = p.mint }, label = { Text("${p.symbol ?: p.mint.take(5)} · POSITION") })
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TerminalMetric("MC", market.marketCapUsd?.let(::formatUsd) ?: "UNKNOWN", Modifier.weight(1f))
                    TerminalMetric("LIQ", market.liquidityUsd?.let(::formatUsd) ?: "UNKNOWN", Modifier.weight(1f))
                    TerminalMetric("VOL 60S", market.volumeUsd60s?.let(::formatUsd) ?: "UNKNOWN", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TerminalMetric("BUYS", market.buyCount60s?.toString() ?: "UNKNOWN", Modifier.weight(1f))
                    TerminalMetric("SELLS", market.sellCount60s?.toString() ?: "UNKNOWN", Modifier.weight(1f))
                    TerminalMetric("BUY FLOW", market.buyVolumeUsd60s?.let(::formatUsd) ?: "UNKNOWN", Modifier.weight(1f))
                    TerminalMetric("SELL FLOW", market.sellVolumeUsd60s?.let(::formatUsd) ?: "UNKNOWN", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TerminalMetric("BUY PRESSURE", market.buyPressurePct60s?.let { "%.0f%%".format(it) } ?: "UNKNOWN", Modifier.weight(1f))
                    TerminalMetric("AGE", selectedToken?.let { relativeAge(System.currentTimeMillis() - it.firstSeenAtEpochMs) } ?: "UNKNOWN", Modifier.weight(1f))
                    TerminalMetric("LIVE RANK", selectedRank?.rank?.score?.toString() ?: "GATHERING", Modifier.weight(1f))
                }
            }
        }

        item {
            TerminalCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LIVE PRICE CHART", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(market.status.name, color = statusColor(market.status), style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    availableTimeframes.forEach { seconds ->
                        FilterChip(
                            selected = timeframeSeconds == seconds,
                            onClick = { timeframeSeconds = seconds },
                            label = { Text(if (seconds < 60) "${seconds}S" else "${seconds / 60}M") }
                        )
                    }
                }
                LivePriceChart(visiblePoints, markers, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("● BUY", color = BuyGreen, style = MaterialTheme.typography.labelSmall)
                    Text("● SELL", color = SellRed, style = MaterialTheme.typography.labelSmall)
                    Text("● SIGNAL", color = NeutralBlue, style = MaterialTheme.typography.labelSmall)
                    Text("${visiblePoints.size} real observations", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        item {
            TerminalCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("PAPER ORDER", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("SIMULATED ONLY", color = WatchAmber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
                Text("LIVE MARKET  ${livePrice?.let(::formatPrice) ?: "UNKNOWN"}", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(10, 25, 50, 100).forEach { preset ->
                        FilterChip(selected = presetAmount == preset, onClick = { presetAmount = preset; buyAmountText = preset.toString() }, label = { Text("\$$preset") })
                    }
                    FilterChip(selected = presetAmount == 0, onClick = { presetAmount = 0 }, label = { Text("CUSTOM") })
                }
                if (presetAmount == 0) {
                    OutlinedTextField(
                        value = buyAmountText,
                        onValueChange = { buyAmountText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) },
                        label = { Text("Buy amount (USD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Est. tokens  ${estimatedBuyQuantity?.let { "%.4g".format(it) } ?: "UNKNOWN"}", style = MaterialTheme.typography.labelSmall)
                    Text("Fee  ${estimatedBuyFee.takeIf { buyAmount > 0.0 }?.let(::formatUsd) ?: "UNKNOWN"}", style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Est. execution  ${estimatedBuyExecution?.let(::formatPrice) ?: "UNKNOWN"}", style = MaterialTheme.typography.labelSmall)
                    Text("Slip  ${if (canTrade && buyAmount > 0.0) "%.2f%%".format(buySlip * 100) else "UNKNOWN"}", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { scope.launch { actionStatus = tradeMessage(vm.paperBuy(mint ?: "", buyAmount)) } },
                    enabled = canTrade && buyAmount > 0.0,
                    colors = ButtonDefaults.buttonColors(containerColor = BuyGreen),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (canTrade) "PAPER BUY  ·  ${formatUsd(buyAmount)}" else "PAPER BUY  ·  WAITING FOR FRESH LIVE TRADE", color = Color.Black, fontWeight = FontWeight.Bold) }

                HorizontalDivider(color = TextMuted.copy(alpha = 0.25f))
                Text("SELL POSITION", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(0.25f, 0.50f, 0.75f, 1f).forEach { fraction ->
                        FilterChip(selected = sellFraction == fraction, onClick = { sellFraction = fraction }, label = { Text("${(fraction * 100).toInt()}%") }, enabled = position != null)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Est. received  ${estimatedSellReceived?.let(::formatUsd) ?: "UNKNOWN"}", style = MaterialTheme.typography.labelSmall)
                    Text("Fee  ${estimatedSellFee?.let(::formatUsd) ?: "UNKNOWN"}", style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Execution  ${estimatedSellExecution?.let(::formatPrice) ?: "UNKNOWN"}", style = MaterialTheme.typography.labelSmall)
                    Text("Slip  ${if (canTrade && sellQuantity > 0.0) "%.2f%%".format(estimatedSellSlip * 100) else "UNKNOWN"}", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { scope.launch { actionStatus = tradeMessage(vm.paperSell(mint ?: "", sellQuantity)) } },
                    enabled = canTrade && sellQuantity > 0.0,
                    colors = ButtonDefaults.buttonColors(containerColor = SellRed),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (canTrade) "PAPER SELL  ·  ${(sellFraction * 100).toInt()}%" else "PAPER SELL  ·  WAITING FOR FRESH LIVE TRADE", color = Color.White, fontWeight = FontWeight.Bold) }
                Text("Simulation only • 0.30% fee • estimated impact capped at 15% • no wallet, signing, or chain transaction", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                actionStatus?.let { Text(it, color = if (it.startsWith("Rejected")) SellRed else BuyGreen, style = MaterialTheme.typography.bodySmall) }
            }
        }

        if (selectedSignal != null) {
            item {
                TerminalCard {
                    Text("SIGNAL CONTEXT", fontWeight = FontWeight.Bold, color = NeutralBlue)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${selectedSignal.signalType} · ${selectedSignal.lifecycleState ?: ""}")
                        Text("${selectedSignal.score}/100", fontWeight = FontWeight.Bold)
                    }
                    Text("Time ${relativeAge(System.currentTimeMillis() - selectedSignal.timestamp)} ago · Signal price ${selectedSignal.priceUsd?.let(::formatPrice) ?: "UNKNOWN"}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text("MC ${selectedSignal.marketCapUsd?.let(::formatUsd) ?: "UNKNOWN"} · Liquidity ${selectedSignal.liquidityUsd?.let(::formatUsd) ?: "UNKNOWN"} · Momentum ${selectedSignal.momentumScore ?: "UNKNOWN"} · Risk ${selectedSignal.manipulationRiskScore ?: "UNKNOWN"}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }

        item {
            TerminalCard {
                Text("PAPER WALLET", fontWeight = FontWeight.Bold)
                val cash = portfolio?.cashUsd ?: analytics.cashUsd
                val invested = positions.sumOf { it.investedUsd }
                val marksAreFresh = positions.all { vm.canPaperTrade(it.mint) }
                val markedValue = if (marksAreFresh) positions.sumOf { p -> (marketStates[p.mint]?.priceUsd ?: 0.0) * p.quantity } else null
                val equity = markedValue?.let { cash + it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TerminalMetric("CASH", formatUsd(cash), Modifier.weight(1f))
                    TerminalMetric("INVESTED", formatUsd(invested), Modifier.weight(1f))
                    TerminalMetric("EQUITY", equity?.let(::formatUsd) ?: "UNKNOWN", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TerminalMetric("UNREALIZED", analytics.unrealizedPnlUsd?.let(::signedUsd) ?: "UNKNOWN", Modifier.weight(1f))
                    TerminalMetric("REALIZED", signedUsd(analytics.realizedPnlUsd), Modifier.weight(1f))
                    TerminalMetric("TOTAL P/L", analytics.unrealizedPnlUsd?.let { signedUsd(analytics.realizedPnlUsd + it) } ?: "UNKNOWN", Modifier.weight(1f))
                }
                if (!marksAreFresh && positions.isNotEmpty()) Text("Some positions lack a fresh PumpPortal trade quote; equity and unrealized P/L are UNKNOWN, not stale-price estimates.", color = WatchAmber, style = MaterialTheme.typography.labelSmall)
                Text("${positions.size} open positions · ${analytics.closedTrades} closed · win rate ${analytics.winRate?.let { "%.0f%%".format(it * 100) } ?: "UNKNOWN"}", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }

        item { Text("OPEN POSITIONS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        if (positions.isEmpty()) item { Text("No open paper positions", color = TextMuted, style = MaterialTheme.typography.bodySmall) }
        items(positions, key = { "position-${it.mint}" }) { p -> PositionRow(p, marketStates[p.mint], vm.canPaperTrade(p.mint), onClick = { selectedMint = p.mint }) }

        item {
            TerminalCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AUTO PAPER", fontWeight = FontWeight.Bold)
                        Text(if (autoStatus.enabled) "ON · simulated entries/exits only" else "OFF · manual mode", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = autoStatus.enabled, onCheckedChange = vm::setAutoPaperTrading)
                }
                Text("${autoStatus.state} · ${autoStatus.lastDecision}", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }

        item { Text("EXECUTION HISTORY", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        if (trades.isEmpty()) item { Text("No paper fills yet", color = TextMuted, style = MaterialTheme.typography.bodySmall) }
        items(trades.take(25), key = { "trade-${it.id}" }) { trade -> TradeHistoryRow(trade) }

        item {
            Text("Market event flow · PumpPortal only", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        val recentMarketTrades = market.recentTrades.takeLast(12).asReversed()
        if (recentMarketTrades.isEmpty()) item { Text("No actual trade events available. Dex snapshots are not shown as trade flow.", color = TextMuted, style = MaterialTheme.typography.bodySmall) }
        items(recentMarketTrades, key = { "event-${it.signature ?: it.timestampMs.toString()}" }) { tick ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tick.side.name, color = if (tick.side.name == "BUY") BuyGreen else SellRed, fontWeight = FontWeight.Bold)
                Text(tick.amountUsd?.let(::formatUsd) ?: "UNKNOWN SIZE", color = TextMuted)
                Text("${relativeAge(System.currentTimeMillis() - tick.timestampMs)} ago", color = TextMuted)
            }
        }
    }
}

@Composable
private fun TerminalCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
    }
}

@Composable
private fun TerminalMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 7.dp, vertical = 5.dp)) {
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusPill(status: MarketDataStatus, source: MarketDataSource) {
    val color = statusColor(status)
    Column(horizontalAlignment = Alignment.End) {
        Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
            Text(status.name, color = color, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Text(if (source == MarketDataSource.DEXSCREENER) "REST SNAPSHOT" else source.name, color = TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PositionRow(position: PaperPositionEntity, state: LiveMarketState?, liveQuoteFresh: Boolean, onClick: () -> Unit) {
    val price = state?.priceUsd?.takeIf { liveQuoteFresh }
    val value = price?.times(position.quantity)
    val pnl = price?.let { (it - position.averageEntryPriceUsd) * position.quantity }
    val pnlPct = price?.let { if (position.averageEntryPriceUsd > 0.0) (it / position.averageEntryPriceUsd - 1.0) * 100.0 else null }
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceRaised), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(position.symbol ?: position.mint.take(8), fontWeight = FontWeight.Bold)
                Text(state?.status?.name ?: "UNKNOWN", color = state?.status?.let(::statusColor) ?: TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            Text("Entry ${formatPrice(position.averageEntryPriceUsd)} · Live ${price?.let(::formatPrice) ?: "UNKNOWN"} · Qty ${"%.5g".format(position.quantity)}", style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Value ${value?.let(::formatUsd) ?: "UNKNOWN"}", style = MaterialTheme.typography.bodySmall)
                Text("${pnl?.let(::signedUsd) ?: "P/L UNKNOWN"}  ${pnlPct?.let { "%+.2f%%".format(it) } ?: ""}", color = pnl?.let { if (it >= 0) BuyGreen else SellRed } ?: TextMuted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TradeHistoryRow(trade: PaperTradeEntity) {
    val buy = trade.side == "PAPER_BUY"
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (buy) "BUY" else "SELL", color = if (buy) BuyGreen else SellRed, fontWeight = FontWeight.Bold)
            Text(trade.symbol ?: trade.mint.take(6), fontWeight = FontWeight.Medium)
            Column(horizontalAlignment = Alignment.End) {
                Text(formatPrice(trade.fillPriceUsd), style = MaterialTheme.typography.labelSmall)
                Text("${relativeAge(System.currentTimeMillis() - trade.timestamp)} · ${trade.marketDataSource} · ${if (trade.simulated) "SIMULATED" else "UNVERIFIED"} · fee ${formatUsd(trade.feeUsd)} · ${trade.realizedPnlUsd?.let(::signedUsd) ?: "—"}", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun estimateSlippage(notionalUsd: Double, liquidityUsd: Double?): Double {
    if (notionalUsd <= 0.0) return 0.0
    val liquidity = liquidityUsd?.takeIf { it > 0.0 } ?: return 0.15
    return min(0.15, notionalUsd / liquidity * 0.5)
}

private fun tradeMessage(result: PaperTradeResult): String = when (result) {
    is PaperTradeResult.Success -> "Filled ${"%.5g".format(result.quantity)} @ ${formatPrice(result.fillPriceUsd)} · fee ${formatUsd(result.feeUsd)} · simulated"
    is PaperTradeResult.Rejected -> "Rejected · ${result.reason}"
}

private fun statusColor(status: MarketDataStatus): Color = when (status) {
    MarketDataStatus.LIVE -> BuyGreen
    MarketDataStatus.STALE -> WatchAmber
    MarketDataStatus.DISCONNECTED -> SellRed
    MarketDataStatus.UNKNOWN -> TextMuted
}

private fun priceColor(state: LiveMarketState, price: Double?): Color = when {
    price == null -> TextMuted
    state.status == MarketDataStatus.LIVE -> BuyGreen
    else -> WatchAmber
}

private fun formatPrice(value: Double): String = when {
    value >= 1.0 -> "$%.4f".format(value)
    value >= 0.001 -> "$%.6f".format(value)
    else -> "$%.10f".format(value)
}

private fun formatUsd(value: Double): String = when {
    value >= 1_000_000 -> "$%.2fM".format(value / 1_000_000)
    value >= 10_000 -> "$%.1fK".format(value / 1_000)
    else -> "$%.2f".format(value)
}

private fun signedUsd(value: Double): String = (if (value >= 0.0) "+" else "") + formatUsd(value)

private fun relativeAge(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        else -> "${seconds / 3600}h"
    }
}
