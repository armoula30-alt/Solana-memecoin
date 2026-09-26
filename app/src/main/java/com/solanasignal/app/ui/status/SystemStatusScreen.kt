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
    val tradeSubsActive by vm.trackedSubscriptionCount.collectAsState()
    val dexEnriched by vm.dexScreenerEnrichedCount.collectAsState()
    val tradesReceived by vm.tradesReceivedCount.collectAsState()
    val mockMode by vm.settings.mockMode.collectAsState()
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("System Status", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    StatusRow("PumpPortal WebSocket", connection.name)
                    StatusRow("API Key", if (apiKeyConfigured) "CONFIGURED" else "NOT SET")
                    StatusRow("Foreground service", if (running) "RUNNING" else "STOPPED")
                    StatusRow("Tokens discovered (free)", tokens.size.toString())
                    StatusRow("Trade subscriptions sent", tradeSubsActive.toString())
                    StatusRow("Trades actually received (metered)", tradesReceived.toString())
                    StatusRow("DexScreener enriched (last pass)", if (mockMode) "N/A (mock mode)" else dexEnriched.toString())
                    StatusRow("Events/sec (live)", eventsPerSec.toString())
                    StatusRow("Reconnects this session", reconnects.toString())
                    StatusRow("Parser errors", parserErrors.toString())
                    StatusRow("Database", "OK")
                }
            }
        }

        if (!mockMode && running && tradeSubsActive > 0 && tradesReceived == 0) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "0 trades received despite $tradeSubsActive active subscriptions. New-token discovery " +
                            "is free and works regardless of your key - trade data is metered (0.01 SOL / 10,000 " +
                            "events) and PumpPortal will silently drop the subscription if your key is invalid or " +
                            "its balance is exhausted. Check your balance on PumpPortal's own site. Also check " +
                            "\"Recent System Events\" below - any error or acknowledgement PumpPortal sends back " +
                            "that isn't a token/trade event now shows up there with its raw content.",
                        Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        if (tradeSubsActive == 0 && tokens.isNotEmpty() && !mockMode) {
            item {
                Text(
                    "No trade subscriptions are active at all, so no BUY/SELL/WATCH signals can be produced. " +
                        "Check Settings for the API key.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
