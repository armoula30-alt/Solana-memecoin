package com.solanasignal.app.ui.scanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solanasignal.app.ui.AppViewModel
import com.solanasignal.app.ui.theme.BuyGreen
import com.solanasignal.app.ui.theme.SellRed
import com.solanasignal.app.ui.theme.WatchAmber

@Composable
fun LiveScannerScreen(vm: AppViewModel, onOpenToken: (String) -> Unit) {
    val rankedTokens by vm.rankedTokens.collectAsState()
    val signals by vm.signals.collectAsState()

    // Latest signal (if any) per mint - used only for the status badge (BUY/SELL/WATCH),
    // not for the live numbers, since a signal may not have fired recently even though
    // the token has plenty of live trade activity (cooldown/dedupe suppresses repeats).
    val latestSignalByMint = remember(signals) { signals.groupBy { it.mint }.mapValues { it.value.first() } }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Live Scanner", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        items(rankedTokens, key = { it.token.mint }) { ranked ->
            val token = ranked.token
            val signal = latestSignalByMint[token.mint]
            val ageSec = (System.currentTimeMillis() - token.firstSeenAtEpochMs) / 1000
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenToken(token.mint) }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("\$${token.symbol ?: token.mint.take(6)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        StatusBadge(signal?.signalType ?: ranked.rank.state)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MiniStat("Age", formatAge(ageSec))
                        MiniStat("Price", ranked.market.priceUsd?.let { "\$${formatCompact(it)}" } ?: "UNKNOWN")
                        MiniStat("MC", ranked.market.marketCapUsd?.let { "\$${formatCompact(it)}" } ?: "UNKNOWN")
                        MiniStat("Status", ranked.market.status.name)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MiniStat("B/S (60s)", "${ranked.market.buyCount60s?.toString() ?: "UNKNOWN"} / ${ranked.market.sellCount60s?.toString() ?: "UNKNOWN"}")
                        MiniStat("Live rank", ranked.rank.score?.toString() ?: "GATHERING")
                        MiniStat("Momentum 60s", ranked.rank.return60sPct?.let { "%+.2f%%".format(it) } ?: "UNKNOWN")
                        MiniStat("Signal", signal?.signalType ?: "—")
                    }
                }
            }
        }
        if (rankedTokens.isEmpty()) {
            item { Text("No tokens discovered yet. Start the scanner from Dashboard.", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusBadge(type: String) {
    val color = when (type) {
        "BUY" -> BuyGreen
        "SELL" -> SellRed
        "WATCH" -> WatchAmber
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    AssistChip(onClick = {}, label = { Text(if (type == "BUY") "BUY CANDIDATE" else type) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color))
}

private fun formatAge(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return "${m}m ${s}s"
}

private fun formatCompact(value: Double): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000)
    value >= 1_000 -> "%.1fK".format(value / 1_000)
    else -> "%.0f".format(value)
}
