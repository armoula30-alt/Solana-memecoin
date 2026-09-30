package com.solanasignal.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solanasignal.app.data.pumpportal.ConnectionState
import com.solanasignal.app.data.room.entities.SignalEntity
import com.solanasignal.app.data.room.entities.TokenEntity
import com.solanasignal.app.ui.AppViewModel
import com.solanasignal.app.ui.theme.BuyGreen
import com.solanasignal.app.ui.theme.NeutralBlue
import com.solanasignal.app.ui.theme.SellRed
import com.solanasignal.app.ui.theme.SurfaceRaised
import com.solanasignal.app.ui.theme.TextMuted
import com.solanasignal.app.ui.theme.WatchAmber

@Composable
fun DashboardScreen(vm: AppViewModel, onOpenToken: (String) -> Unit) {
    val running by vm.running.collectAsState()
    val connection by vm.connectionState.collectAsState()
    val tokens by vm.tokens.collectAsState()
    val signals by vm.signals.collectAsState()
    val mockMode by vm.settings.mockMode.collectAsState()
    val solPrice by vm.solUsdPrice.collectAsState()
    val candidateCount by vm.candidateCount.collectAsState()
    val feedSubscriptions by vm.activeFeedSubscriptions.collectAsState()
    val feedProvider by vm.feedProvider.collectAsState()
    val tradeProvider by vm.tradeProvider.collectAsState()
    var signalsToday by remember { mutableStateOf(0) }

    LaunchedEffect(signals.size) { signalsToday = vm.signalCountToday() }

    val tokensByMint = remember(tokens) { tokens.associateBy { it.mint } }
    val activeSignals = signals.filter { it.signalType != "REJECTED" }.take(20)
    val avgScore = signals.takeIf { it.isNotEmpty() }?.map { it.score }?.average() ?: 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SOLANA SIGNAL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Real-time intelligence terminal", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                StatusBadge(if (running) connectionLabel(connection) else "STOPPED", if (running) connectionColor(connection) else SellRed)
            }
            if (mockMode) {
                Spacer(Modifier.height(6.dp))
                AssistChip(onClick = {}, label = { Text("MOCK DATA") })
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceRaised), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Market feed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Discovery: $feedProvider  ${connectionLabel(connection)}", color = connectionColor(connection), style = MaterialTheme.typography.bodySmall)
                            Text("Trades: $tradeProvider", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = running, onCheckedChange = { if (it) vm.startScanner() else vm.stopScanner() })
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("SOL/USD  ${solPrice?.let { "$%.2f".format(it) } ?: "UNKNOWN"}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("CANDIDATES", candidateCount.toString(), Modifier.weight(1f))
                StatCard("FEED SUBS", feedSubscriptions.toString(), Modifier.weight(1f))
                StatCard("ACTIVE", activeSignals.size.toString(), Modifier.weight(1f))
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("TODAY", signalsToday.toString(), Modifier.weight(1f))
                StatCard("AVG SCORE", "%.0f".format(avgScore), Modifier.weight(1f))
                StatCard("SOL", solPrice?.let { "$%.0f".format(it) } ?: "?", Modifier.weight(1f))
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ACTIVE SIGNALS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${activeSignals.size} tracked", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (activeSignals.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceRaised), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("No active signals", fontWeight = FontWeight.Bold)
                        Text("Monitoring the market...", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            items(activeSignals, key = { it.id }) { signal ->
                TokenSignalCard(signal, tokensByMint[signal.mint], onOpenToken)
            }
        }
    }
}

@Composable
private fun TokenSignalCard(signal: SignalEntity, token: TokenEntity?, onOpenToken: (String) -> Unit) {
    val stateColor = signalColor(signal.lifecycleState ?: signal.signalType)
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
        modifier = Modifier.fillMaxWidth().clickable { onOpenToken(signal.mint) }
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("\$${signal.symbol ?: signal.mint.take(6)}", fontWeight = FontWeight.Bold)
                    Text(signal.mint.take(12) + "...  •  ${formatAge(System.currentTimeMillis() - signal.timestamp)}", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
                Text("${signal.score}/100", color = stateColor, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(token?.lastPriceUsd?.let { "$%.8f".format(it) } ?: "Price UNKNOWN", style = MaterialTheme.typography.bodySmall)
                Text(token?.dexPriceChange5mPct?.let { "%+.2f%%".format(it) } ?: "Change UNKNOWN", color = token?.dexPriceChange5mPct?.let { if (it >= 0) BuyGreen else SellRed } ?: TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("MC ${token?.marketCapUsd?.let { "$%.0f".format(it) } ?: "UNKNOWN"}", style = MaterialTheme.typography.labelSmall)
                Text("LP ${token?.liquidityUsd?.let { "$%.0f".format(it) } ?: "UNKNOWN"}", style = MaterialTheme.typography.labelSmall)
                Text("Risk ${token?.manipulationRiskLevel ?: "UNKNOWN"}", color = riskColor(token?.manipulationRiskLevel), style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Momentum ${token?.momentumScore ?: "?"}", style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(progress = ((token?.momentumScore ?: 0) / 100f).coerceIn(0f, 1f), modifier = Modifier.weight(1f), color = stateColor)
                Text(signal.lifecycleState ?: signal.signalType, color = stateColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceRaised), modifier = modifier) {
        Column(Modifier.padding(9.dp)) {
            Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    AssistChip(onClick = {}, label = { Text(label, color = color) })
}

private fun signalColor(state: String): Color = when (state) {
    "BUY", "EARLY_MOMENTUM", "STRONG_MOMENTUM", "EXTREME_MOMENTUM" -> BuyGreen
    "SELL", "INVALIDATED", "AVOID" -> SellRed
    "WATCH", "WEAKENING" -> WatchAmber
    else -> NeutralBlue
}

private fun riskColor(level: String?): Color = when (level) {
    "HIGH", "CRITICAL" -> SellRed
    "MODERATE", "MEDIUM" -> WatchAmber
    "LOW" -> BuyGreen
    else -> TextMuted
}

private fun connectionColor(state: ConnectionState): Color = when (state) {
    ConnectionState.CONNECTED -> BuyGreen
    ConnectionState.DEGRADED, ConnectionState.RECONNECTING -> WatchAmber
    else -> SellRed
}

private fun connectionLabel(state: ConnectionState): String = when (state) {
    ConnectionState.CONNECTED -> "LIVE"
    ConnectionState.CONNECTING -> "CONNECTING"
    ConnectionState.DEGRADED -> "DEGRADED"
    ConnectionState.RECONNECTING -> "RECONNECTING"
    ConnectionState.DISCONNECTED -> "DISCONNECTED"
}

private fun formatAge(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    return when {
        seconds < 60 -> "${seconds}s old"
        seconds < 3600 -> "${seconds / 60}m old"
        else -> "${seconds / 3600}h old"
    }
}
