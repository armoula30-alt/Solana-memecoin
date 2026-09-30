package com.solanasignal.app.ui.status

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solanasignal.app.domain.scanner.TokenDiagnostics
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
    val pumpDevKeyConfigured by vm.pumpDevApiKeyConfigured.collectAsState()
    val pumpDevTrades by vm.pumpDevTradeEvents.collectAsState()
    val normalizedTrades by vm.normalizedTradeCount.collectAsState()
    val discoveryTokens by vm.discoveryTokens.collectAsState()
    val initialFilterEvaluations by vm.initialFilterEvaluations.collectAsState()
    val discoveryDecisionPassed by vm.discoveryDecisionPassed.collectAsState()
    val discoveryDecisionRejected by vm.discoveryDecisionRejected.collectAsState()
    val discoveryDecisionUnknown by vm.discoveryDecisionUnknown.collectAsState()
    val discoveryFilterPassed by vm.discoveryFilterPassed.collectAsState()
    val discoveryFilterRejected by vm.discoveryFilterRejected.collectAsState()
    val discoveryFilterUnknown by vm.discoveryFilterUnknown.collectAsState()
    val liveFilterEvaluations by vm.liveFilterEvaluations.collectAsState()
    val liveFilterPassed by vm.liveFilterPassed.collectAsState()
    val liveFilterRejected by vm.liveFilterRejected.collectAsState()
    val liveFilterUnknown by vm.liveFilterUnknown.collectAsState()
    val deduplicatedTrades by vm.deduplicatedTrades.collectAsState()
    val metricsUpdates by vm.metricsUpdates.collectAsState()
    val signalEvaluations by vm.signalEvaluations.collectAsState()
    val signalsEmitted by vm.signalsEmitted.collectAsState()
    val evaluationsRejected by vm.evaluationsRejected.collectAsState()
    val lastRejectionReason by vm.lastRejectionReason.collectAsState()
    val tokenDiagnostics by vm.tokenDiagnostics.collectAsState()
    val dexEnriched by vm.dexScreenerEnrichedCount.collectAsState()
    val tradesReceived by vm.tradesReceivedCount.collectAsState()
    val mockMode by vm.settings.mockMode.collectAsState()
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    var diagnosticsExpanded by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("System Status", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    StatusRow("Discovery provider", feedProvider)
                    StatusRow("Trade provider", tradeProvider)
                    StatusRow("PumpDev key", if (pumpDevKeyConfigured) "CONFIGURED" else "NOT CONFIGURED")
                    StatusRow("Feed connection", connection.name)
                    StatusRow("PumpPortal API key", if (apiKeyConfigured) "OPTIONAL / CONFIGURED" else "NOT REQUIRED")
                    StatusRow("Foreground service", if (running) "RUNNING" else "STOPPED")
                    StatusRow("Discovery tokens", discoveryTokens.toString())
                    StatusRow("Initial Filter evaluations", initialFilterEvaluations.toString())
                    StatusRow("Final discovery decisions passed", discoveryDecisionPassed.toString())
                    StatusRow("Final discovery decisions rejected", discoveryDecisionRejected.toString())
                    StatusRow("Final discovery decisions unknown", discoveryDecisionUnknown.toString())
                    StatusRow("Discovery filters passed", discoveryFilterPassed.toString())
                    StatusRow("Discovery filters rejected", discoveryFilterRejected.toString())
                    StatusRow("Discovery filters unknown", discoveryFilterUnknown.toString())
                    StatusRow("Live filter evaluations", liveFilterEvaluations.toString())
                    StatusRow("Live filters passed", liveFilterPassed.toString())
                    StatusRow("Live filters rejected", liveFilterRejected.toString())
                    StatusRow("Live filters unknown", liveFilterUnknown.toString())
                    StatusRow("Candidates tracked", candidateCount.toString())
                    StatusRow("PumpDev trade subscriptions", feedSubscriptions.toString())
                    StatusRow("PumpDev trades received", pumpDevTrades.toString())
                    StatusRow("PumpDev trades normalized", normalizedTrades.toString())
                    StatusRow("Deduplicated trades", deduplicatedTrades.toString())
                    StatusRow("Metrics updates", metricsUpdates.toString())
                    StatusRow("Signal evaluations", signalEvaluations.toString())
                    StatusRow("Signals emitted", signalsEmitted.toString())
                    StatusRow("Evaluations rejected", evaluationsRejected.toString())
                    StatusRow("Last rejection reason", lastRejectionReason ?: "UNKNOWN")
                    StatusRow("DexScreener enriched (last pass)", if (mockMode) "N/A (mock mode)" else dexEnriched.toString())
                    StatusRow("Events/sec (live)", eventsPerSec.toString())
                    StatusRow("Reconnects this session", reconnects.toString())
                    StatusRow("Parser errors", parserErrors.toString())
                    StatusRow("Database", "OK")
                }
            }
        }

        item {
            TextButton(onClick = { diagnosticsExpanded = !diagnosticsExpanded }) {
                Text(if (diagnosticsExpanded) "Hide per-token diagnostics" else "Show per-token diagnostics (${tokenDiagnostics.size})")
            }
        }
        if (diagnosticsExpanded) {
            items(tokenDiagnostics.take(20), key = { it.mint }) { diagnostic ->
                TokenDiagnosticsCard(diagnostic, sdf)
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

@Composable
private fun TokenDiagnosticsCard(d: TokenDiagnostics, sdf: SimpleDateFormat) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${d.symbol ?: "UNKNOWN"}  ${d.mint}", fontWeight = FontWeight.Bold)
            Text("Trades ${d.tradeCount} | BUY ${d.buyCount} | SELL ${d.sellCount} | Pressure ${percent(d.buyPressure)}")
            Text("Volume BUY ${money(d.buyVolumeUsd)} | SELL ${money(d.sellVolumeUsd)}")
            Text("Unique buyers ${d.uniqueBuyers} | sellers ${d.uniqueSellers}")
            Text("Price ${money(d.latestPriceUsd)} | Δ10s ${percent(d.priceChangeByWindow[10])} | Δ1m ${percent(d.priceChangeByWindow[60])} | Δ5m ${percent(d.priceChangeByWindow[300])}")
            Text("Market cap ${money(d.marketCapUsd)} | Δ1m ${percent(d.marketCapChangeByWindow[60])} | Δ5m ${percent(d.marketCapChangeByWindow[300])} | liquidity ${money(d.liquidityUsd)}")
            Text("Age ${d.ageSeconds?.let { "${it}s" } ?: "UNKNOWN"} | first ${d.firstTradeAtEpochMs?.let { sdf.format(Date(it)) } ?: "UNKNOWN"} | latest ${d.latestTradeAtEpochMs?.let { sdf.format(Date(it)) } ?: "UNKNOWN"}")
            Text("Valid samples 5s:${samples(d, 5)} 10s:${samples(d, 10)} 15s:${samples(d, 15)} 30s:${samples(d, 30)} 60s:${samples(d, 60)} 2m:${samples(d, 120)} 5m:${samples(d, 300)}")
            Text("Signal ${d.signalType ?: "UNKNOWN"} | reason ${d.signalReason ?: "UNKNOWN"}")
        }
    }
}

private fun samples(d: TokenDiagnostics, seconds: Int): String =
    d.validSamplesByWindow[seconds]?.toString() ?: "UNKNOWN"

private fun money(value: Double?): String =
    value?.let { String.format(Locale.US, "$%.4f", it) } ?: "UNKNOWN"

private fun percent(value: Double?): String =
    value?.let { String.format(Locale.US, "%.2f%%", it) } ?: "UNKNOWN"
