package com.solanasignal.app.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solanasignal.app.photon.PhotonLauncher
import com.solanasignal.app.ui.AppViewModel
import org.json.JSONArray

@Composable
fun TokenDetailScreen(vm: AppViewModel, mint: String) {
    val context = LocalContext.current
    val tokens by vm.tokens.collectAsState()
    val signals by vm.signals.collectAsState()

    val token = tokens.find { it.mint == mint }
    val latestSignal = signals.filter { it.mint == mint }.maxByOrNull { it.timestamp }

    val reasons = remember(latestSignal) {
        latestSignal?.reasonsJson?.let { json ->
            runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }
            }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("$${token?.symbol ?: mint.take(6)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(token?.name ?: "Unknown name", style = MaterialTheme.typography.bodyMedium)
        }

        item {
            DetailCard("Token") {
                Row("Mint", mint.take(10) + "...")
                Row("Creator", token?.creator?.take(10)?.plus("...") ?: "UNKNOWN")
                Row("Market Cap", token?.marketCapUsd?.let { "$%.0f".format(it) } ?: "UNKNOWN")
                Row("Liquidity", token?.liquidityUsd?.let { "$%.0f".format(it) } ?: "UNKNOWN")
                Row("Price", token?.lastPriceUsd?.let { "$%.8f".format(it) } ?: "UNKNOWN")
            }
        }

        latestSignal?.let { s ->
            item {
                DetailCard("Metrics") {
                    Row("Buyers", s.buyers.toString())
                    Row("Sellers", s.sellers.toString())
                    Row("Buy Volume", "$%.0f".format(s.buyVolumeUsd))
                    Row("Sell Volume", "$%.0f".format(s.sellVolumeUsd))
                }
            }
            item {
                DetailCard("Score") {
                    Row("Momentum Score", "${s.score}/100")
                    Row("Signal", s.signalType)
                }
            }
            item {
                DetailCard("WHY THIS SIGNAL?") {
                    if (reasons.isEmpty()) {
                        Text("No reasons recorded for this evaluation.")
                    } else {
                        reasons.forEach { Text("\u2713 $it") }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { PhotonLauncher.openForToken(context, mint, token?.symbol) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("OPEN IN PHOTON") }
            Text(
                "This app does not execute trades. You decide and trade manually in Photon.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun Row(label: String, value: String) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
