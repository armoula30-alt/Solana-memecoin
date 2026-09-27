package com.solanasignal.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solanasignal.app.data.pumpportal.ConnectionState
import com.solanasignal.app.ui.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(vm: AppViewModel, onOpenToken: (String) -> Unit) {
    val running by vm.running.collectAsState()
    val connection by vm.connectionState.collectAsState()
    val tokens by vm.tokens.collectAsState()
    val signals by vm.signals.collectAsState()
    val mockMode by vm.settings.mockMode.collectAsState()
    val scope = rememberCoroutineScope()
    var signalsToday by remember { mutableStateOf(0) }

    LaunchedEffect(signals.size) { signalsToday = vm.signalCountToday() }

    val buySignals = signals.count { it.signalType == "BUY" }
    val sellSignals = signals.count { it.signalType == "SELL" }
    val avgScore = signals.takeIf { it.isNotEmpty() }?.map { it.score }?.average() ?: 0.0
    val maxScore = signals.maxOfOrNull { it.score } ?: 0

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Solana Signal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (mockMode) {
                Spacer(Modifier.height(4.dp))
                AssistChip(onClick = {}, label = { Text("MOCK MODE \u2014 simulated data") })
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Scanner", style = MaterialTheme.typography.titleMedium)
                            Text(if (running) "RUNNING" else "STOPPED", color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        Switch(checked = running, onCheckedChange = {
                            if (it) vm.startScanner() else vm.stopScanner()
                        })
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("PumpPortal: ${connectionLabel(connection)}")
                    val solPrice by vm.solUsdPrice.collectAsState()
                    Text(
                        "SOL/USD: " + (solPrice?.let { "$%.2f".format(it) } ?: "fetching\u2026"),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!mockMode && solPrice == null && running) {
                        Text(
                            "Market cap / liquidity show UNKNOWN until the first SOL/USD price fetch completes (usually a few seconds).",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Tokens discovered", tokens.size.toString(), Modifier.weight(1f))
                val trackedCount by vm.trackedSubscriptionCount.collectAsState()
                StatCard("Mints tracked for Dex", trackedCount.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Signals today", signalsToday.toString(), Modifier.weight(1f))
                StatCard("BUY / SELL", "$buySignals / $sellSignals", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Average score", "%.0f".format(avgScore), Modifier.weight(1f))
                StatCard("Highest score", maxScore.toString(), Modifier.weight(1f))
            }
        }

        item { Text("Recent Signals", style = MaterialTheme.typography.titleMedium) }
        items(signals.take(10)) { s ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenToken(s.mint) }
            ) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("\$${s.symbol ?: s.mint.take(6)}", fontWeight = FontWeight.Bold)
                        Text(s.signalType, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${s.score}/100", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

private fun connectionLabel(state: ConnectionState): String = when (state) {
    ConnectionState.CONNECTED -> "CONNECTED"
    ConnectionState.CONNECTING -> "CONNECTING"
    ConnectionState.DEGRADED -> "DEGRADED"
    ConnectionState.RECONNECTING -> "RECONNECTING"
    ConnectionState.DISCONNECTED -> "DISCONNECTED"
}
