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
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("System Status", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    StatusRow("PumpPortal WebSocket", connection.name)
                    StatusRow("API Key", if (apiKeyConfigured) "CONFIGURED" else "NOT SET")
                    StatusRow("Foreground service", if (running) "RUNNING" else "STOPPED")
                    StatusRow("Tracked tokens", tokens.size.toString())
                    StatusRow("Database", "OK")
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
