package com.solanasignal.app.ui.history

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

private val filterOptions = listOf("ALL", "BUY", "SELL", "WATCH", "REJECTED")

@Composable
fun SignalHistoryScreen(vm: AppViewModel) {
    val signals by vm.signals.collectAsState()
    var selectedFilter by remember { mutableStateOf("ALL") }
    val filtered = remember(signals, selectedFilter) {
        if (selectedFilter == "ALL") signals else signals.filter { it.signalType == selectedFilter }
    }
    val sdf = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.US) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Signal History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyRowFilters(selectedFilter) { selectedFilter = it }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("\$${s.symbol ?: s.mint.take(6)} \u2014 ${s.signalType}", fontWeight = FontWeight.Bold)
                            Text("${s.score}/100")
                        }
                        Text(sdf.format(Date(s.timestamp)), style = MaterialTheme.typography.bodySmall)
                        Text("Lifecycle: ${s.lifecycleState ?: "UNKNOWN"}", style = MaterialTheme.typography.bodySmall)
                        Text("Buyers ${s.buyers} / Sellers ${s.sellers} \u2022 Buy $%.0f / Sell $%.0f".format(s.buyVolumeUsd, s.sellVolumeUsd),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (filtered.isEmpty()) {
                item { Text("No signals yet.") }
            }
        }
    }
}

@Composable
private fun LazyRowFilters(selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(filterOptions) { option ->
            FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option) })
        }
    }
}
