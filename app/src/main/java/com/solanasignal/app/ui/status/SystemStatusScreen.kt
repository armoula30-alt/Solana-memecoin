package com.solanasignal.app.ui.status

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solanasignal.app.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SystemStatusScreen(vm: AppViewModel) {
    val connection by vm.connectionState.collectAsState()
    val running by vm.running.collectAsState()
    val apiKeyConfigured by vm.settings.apiKeyConfigured.collectAsState()
    val tokens by vm.tokens.collectAsState()
    val events by vm.systemEvents.collectAsState()
    val reconnects by vm.reconnectCount.collectAsState()
    val parserErrors by vm.parserErrorCount.collectAsState()
    val eventsPerSec by vm.eventsPerSecond.collectAsState()
    val candidateCount by vm.candidateCount.collectAsState()
    val feedSubscriptions by vm.activeFeedSubscriptions.collectAsState()
    val feedProvider by vm.feedProvider.collectAsState()
    val tradeProvider by vm.tradeProvider.collectAsState()
    val pumpDevTrades by vm.pumpDevTradeEvents.collectAsState()
    val normalizedTrades by vm.normalizedTradeCount.collectAsState()
    val discoveryTokens by vm.discoveryTokens.collectAsState()
    val deduplicatedTrades by vm.deduplicatedTrades.collectAsState()
    val metricsUpdates by vm.metricsUpdates.collectAsState()
    val signalEvaluations by vm.signalEvaluations.collectAsState()
    val dexEnriched by vm.dexScreenerEnrichedCount.collectAsState()
    val tradesReceived by vm.tradesReceivedCount.collectAsState()
    val mockMode by vm.settings.mockMode.collectAsState()
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("System Status", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    StatusRow("Discovery provider", feedProvider)
                    StatusRow("Trade provider", tradeProvider)
                    StatusRow("Feed connection", connection.name)
                    StatusRow("PumpPortal API key", if (apiKeyConfigured) "OPTIONAL / CONFIGURED" else "NOT REQUIRED")
                    StatusRow("Foreground service", if (running) "RUNNING" else "STOPPED")
                    StatusRow("Discovery tokens", discoveryTokens.toString())
                    StatusRow("Candidates tracked", candidateCount.toString())
                    StatusRow("PumpDev trade subscriptions", feedSubscriptions.toString())
                    StatusRow("PumpDev trades received", pumpDevTrades.toString())
                    StatusRow("PumpDev trades normalized", normalizedTrades.toString())
                    StatusRow("Deduplicated trades", deduplicatedTrades.toString())
                    StatusRow("Metrics updates", metricsUpdates.toString())
                    StatusRow("Signal evaluations", signalEvaluations.toString())
                    StatusRow("DexScreener enriched (last pass)", if (mockMode) "N/A (mock mode)" else dexEnriched.toString())
                    StatusRow("Events/sec (live)", eventsPerSec.toString())
                    StatusRow("Reconnects this session", reconnects.toString())
                    StatusRow("Parser errors", parserErrors.toString())
                    StatusRow("Database", "OK")
                }
            }
        }

        if (!mockMode && running && dexEnriched == 0 && tokens.isNotEmpty()) {
            item { Text("DexScreener has not indexed any tracked mint yet; the app will retry automatically.", style = MaterialTheme.typography.bodySmall) }
        }
        if (reconnects > 3 || parserErrors > 0) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        if (reconnects > 3)
                            "High reconnect count usually means the connection is dropping repeatedly - check your network, or that the API key is valid."
                        else
                            "Parser errors mean some messages from PumpPortal didn't match the expected shape - check Recent System Events below.",
                        Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item { Text("Recent System Events", style = MaterialTheme.typography.titleMedium) }
        items(events) { e ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("[${e.category}] ${sdf.format(Date(e.timestamp))}", style = MaterialTheme.typography.labelSmall)
                    Text(e.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (events.isEmpty()) item { Text("No system events recorded yet.") }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold)
    }
}
