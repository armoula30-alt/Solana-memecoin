package com.solanasignal.app.ui.diagnostics

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solanasignal.app.data.telemetry.DiagnosticWindowMetrics
import com.solanasignal.app.ui.AppViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val runtime by vm.diagnosticRuntime.collectAsState()
    val sessionId by vm.diagnosticSessionId.collectAsState()
    val events by vm.diagnosticEvents.collectAsState()
    val tokens by vm.tokens.collectAsState()
    var selectedMint by remember { mutableStateOf<String?>(null) }
    var tokenMenu by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { vm.exportDiagnostics(uri) }
                .onSuccess { count -> exportMessage = "Exported $count events" }
                .onFailure { error -> exportMessage = "Export failed: ${error.message ?: error.javaClass.simpleName}" }
        }
    }
    val mint = selectedMint ?: tokens.firstOrNull()?.mint
    val windows = mint?.let { vm.diagnosticWindowsFor(it) }.orEmpty()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Developer Diagnostics") },
            navigationIcon = { OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("Back") } },
            actions = { Button(onClick = { exporter.launch("solana-diagnostics-${System.currentTimeMillis()}.json") }, modifier = Modifier.padding(end = 8.dp)) { Text("Export JSON") } }
        )
    }) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Source: ${runtime.dataSource}   Connection: ${runtime.connectionState}")
                        Text("Events/s: ${runtime.eventsPerSecond}   Incoming: ${runtime.incomingEvents}   Active tokens: ${runtime.activeTokens}   Subscriptions: ${runtime.subscriptions}")
                        Text("Last event: ${runtime.lastEventReceivedAtMs?.let(::formatTime) ?: "UNKNOWN"}")
                        Text("Latency (source→receive): p50 ${runtime.eventLatencyP50Ms?.let { "${it}ms" } ?: "UNKNOWN"} · p95 ${runtime.eventLatencyP95Ms?.let { "${it}ms" } ?: "UNKNOWN"} · max ${runtime.eventLatencyMaxMs?.let { "${it}ms" } ?: "UNKNOWN"}")
                        Text("Malformed ${runtime.malformedEvents} · Unknown ${runtime.unknownEvents} · Warnings ${runtime.warnings} · Errors ${runtime.errors}")
                        Text("Dropped logs ${runtime.droppedLogCount} · DB writer failures ${runtime.writerFailureCount} · stale tokens ${runtime.staleTokens}")
                        Text("Session: ${sessionId ?: "starting…"}", style = MaterialTheme.typography.labelSmall)
                        exportMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("Rolling diagnostic inputs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Metrics are diagnostic-only; incomplete coverage is UNKNOWN, never imputed.", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = { tokenMenu = true }, enabled = tokens.isNotEmpty()) { Text(mint?.let { tokens.firstOrNull { t -> t.mint == it }?.symbol ?: it.take(8) } ?: "No token") }
                            DropdownMenu(expanded = tokenMenu, onDismissRequest = { tokenMenu = false }) {
                                tokens.take(100).forEach { token ->
                                    DropdownMenuItem(
                                        text = { Text("${token.symbol ?: token.mint.take(8)} · ${token.mint.take(8)}") },
                                        onClick = { selectedMint = token.mint; tokenMenu = false }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        if (mint == null) Text("No tracked tokens yet.")
                        else windows.forEach { WindowRow(it) }
                    }
                }
            }
            item {
                Text("Recent structured events", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (events.isEmpty()) item { Text("No events persisted yet. Events are written asynchronously to local Room storage.") }
            items(events.asReversed(), key = { it.id }) { event ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${event.severity} · ${event.eventType}", fontWeight = FontWeight.SemiBold)
                            Text(event.receivedAtMs.let(::formatTime), style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${event.component} · ${event.dataSource ?: "UNKNOWN"} · ${event.tokenAddress?.take(10) ?: "no mint"}", style = MaterialTheme.typography.labelSmall)
                        Text(event.message, style = MaterialTheme.typography.bodySmall)
                        Text(event.metadataJson.take(900), style = MaterialTheme.typography.labelSmall, maxLines = 5)
                    }
                }
            }
            item { Divider(); Text("Raw frames are retained only while TRACE is enabled. Provider keys are never included in exported data.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 18.dp)) }
        }
    }
}

@Composable
private fun WindowRow(metric: DiagnosticWindowMetrics) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val windowLabel = when (metric.windowSeconds) {
            120 -> "2m"
            300 -> "5m"
            else -> "${metric.windowSeconds}s"
        }
        Text("$windowLabel · ${if (metric.covered) "COVERED" else "UNKNOWN"} · samples ${metric.sampleCount ?: "—"}", fontWeight = FontWeight.Medium)
        Text(
            "buys ${metric.buyCount ?: "—"} / sells ${metric.sellCount ?: "—"} · unique wallets ${metric.uniqueBuyers ?: "—"}/${metric.uniqueSellers ?: "—"} · pressure ${metric.buyPressurePct?.let { "%.1f%%".format(Locale.US, it) } ?: "—"}",
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            "volume ${metric.buyVolumeUsd?.let { "%.2f".format(Locale.US, it) } ?: "—"} / ${metric.sellVolumeUsd?.let { "%.2f".format(Locale.US, it) } ?: "—"} USD · trade freq ${metric.tradeFrequencyPerSecond?.let { "%.3f/s".format(Locale.US, it) } ?: "—"} · persistence ${metric.buyerPersistencePct?.let { "%.0f%%".format(Locale.US, it) } ?: "—"}",
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            "price slope ${metric.priceVelocityPct?.let { "%.2f%%".format(Locale.US, it) } ?: "—"} · MC slope ${metric.marketCapVelocityPct?.let { "%.2f%%".format(Locale.US, it) } ?: "—"} · price/MC curvature ${metric.priceAccelerationPctPerSecond?.let { "%.4f".format(Locale.US, it) } ?: "—"}/${metric.marketCapAccelerationPctPerSecond?.let { "%.4f".format(Locale.US, it) } ?: "—"} · frequency acceleration ${metric.tradeFrequencyAccelerationPerSecond?.let { "%.4f".format(Locale.US, it) } ?: "—"}",
            style = MaterialTheme.typography.labelSmall
        )
        Divider()
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
