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
import com.solanasignal.app.ui.theme.BuyGreen
import com.solanasignal.app.ui.theme.SellRed
import com.solanasignal.app.ui.theme.SurfaceRaised
import com.solanasignal.app.ui.theme.TextMuted
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
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceRaised), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("\$${token?.symbol ?: mint.take(6)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(token?.name ?: "Unknown name", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                        StateBadge(token?.lifecycle ?: "UNKNOWN")
                    }
                    Text("Mint ${mint.take(10)}...", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(token?.lastPriceUsd?.let { "\$%.8f".format(it) } ?: "Price UNKNOWN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(token?.dexPriceChange5mPct?.let { "%+.2f%% since launch".format(it) } ?: "Change UNKNOWN", color = token?.dexPriceChange5mPct?.let { if (it >= 0) BuyGreen else SellRed } ?: TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            DetailCard("Token") {
                Row("Mint", mint.take(10) + "...")
                Row("Pool", token?.poolAddress?.take(10)?.plus("...") ?: "UNKNOWN")
                Row("DEX", token?.dexId ?: "pump.fun bonding curve (pre-index)")
                Row("Creator", token?.creator?.take(10)?.plus("...") ?: "UNKNOWN")
                Row("Lifecycle", token?.lifecycle ?: "UNKNOWN")
                Row("Market Cap", token?.marketCapUsd?.let { "$%.0f".format(it) } ?: "UNKNOWN")
                Row("Liquidity", token?.liquidityUsd?.let { "$%.0f".format(it) } ?: "UNKNOWN")
                Row("Price", token?.lastPriceUsd?.let { "$%.8f".format(it) } ?: "UNKNOWN")
                Row("Data quality", token?.let { t -> t.dataQualityScore?.let { "$it/100 ${t.dataQualityLabel ?: ""}" } } ?: "UNKNOWN")
            }
        }

        token?.let { t ->
            if (t.momentumScore != null || t.manipulationRiskScore != null) {
                item {
                    DetailCard("Advanced Intelligence") {
                        Row("Momentum score", t.momentumScore?.let { "$it/100" } ?: "UNKNOWN")
                        Row("Momentum state", t.momentumState ?: "UNKNOWN")
                        Row("Persistence", t.momentumPersistencePct?.let { "%.0f%%".format(it) } ?: "UNKNOWN")
                        Row("Manipulation risk", t.manipulationRiskScore?.let { "$it/100" } ?: "UNKNOWN")
                        Row("Risk level", t.manipulationRiskLevel ?: "UNKNOWN")
                        JsonListSection("Risk findings", t.manipulationFindingsJson, "⚠")
                    }
                }
            }
            if (t.dexId != null) {
                item {
                    DetailCard("DexScreener") {
                        Row("Pair", t.poolAddress?.take(10)?.plus("...") ?: "UNKNOWN")
                        Row("FDV", t.dexFdVUsd?.let { "$%,.0f".format(it) } ?: "UNKNOWN")
                        Row("5m Volume", t.dexVolume5mUsd?.let { "$%,.0f".format(it) } ?: "UNKNOWN")
                        Row("1h Volume", t.dexVolume1hUsd?.let { "$%,.0f".format(it) } ?: "UNKNOWN")
                        Row("6h Volume", t.dexVolume6hUsd?.let { "$%,.0f".format(it) } ?: "UNKNOWN")
                        Row("24h Volume", t.dexVolume24hUsd?.let { "$%,.0f".format(it) } ?: "UNKNOWN")
                        Row("5m Buys/Sells", "${t.dexBuys5m ?: "?"}/${t.dexSells5m ?: "?"}")
                        Row("1h Buys/Sells", "${t.dexBuys1h ?: "?"}/${t.dexSells1h ?: "?"}")
                        Row("5m Change", t.dexPriceChange5mPct?.let { "%.2f%%".format(it) } ?: "UNKNOWN")
                        Row("1h Change", t.dexPriceChange1hPct?.let { "%.2f%%".format(it) } ?: "UNKNOWN")
                        Row("6h Change", t.dexPriceChange6hPct?.let { "%.2f%%".format(it) } ?: "UNKNOWN")
                        Row("24h Change", t.dexPriceChange24hPct?.let { "%.2f%%".format(it) } ?: "UNKNOWN")
                        Row("Active Boosts", t.dexActiveBoosts?.toString() ?: "UNKNOWN")
                        t.dexUrl?.let { Text("DexScreener: $it", style = MaterialTheme.typography.bodySmall) }
                        t.dexDescription?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "DexScreener لم يفهرس هذه العملة بعد؛ المعروض حاليًا هو بيانات PumpPortal اللحظية.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        token?.let { t ->
            t.aiDecision?.let { decision ->
                item {
                    DetailCard("CodeCraft AI") {
                        Row("Decision", decision)
                        Row("Confidence", t.aiConfidence?.let { "$it/100" } ?: "UNKNOWN")
                        Row("Risk", t.aiRisk ?: "UNKNOWN")
                        t.aiProvider?.let { Row("Provider", it) }
                        t.aiSummary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        JsonListSection("Positive factors", t.aiReasonsJson, "✓")
                        JsonListSection("Negative factors", t.aiNegativeFactorsJson, "−")
                        JsonListSection("Risk flags", t.aiRedFlagsJson, "⚠")
                        JsonListSection("Contradictions", t.aiContradictionsJson, "!")
                        JsonListSection("Missing data", t.aiMissingDataJson, "?")
                        JsonListSection("Monitor", t.aiRecommendedMonitoringJson, "→")
                        Text(
                            if (t.aiShouldNotify == true) "AI recommends monitoring this signal"
                            else "AI advisory only — no automatic trading",
                            style = MaterialTheme.typography.bodySmall
                        )
                        t.aiAnalyzedAtEpochMs?.let { Text("Analyzed ${formatAge(System.currentTimeMillis() - it)} ago", style = MaterialTheme.typography.bodySmall) }
                    }
                }
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
                onClick = { PhotonLauncher.openForToken(context, mint, token?.poolAddress, token?.symbol) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("OPEN IN PHOTON") }
            Text(
                "This app does not execute trades. You decide and trade manually in Photon.",
                style = MaterialTheme.typography.bodySmall
            )
            if (token?.poolAddress == null) {
                Text(
                    "Pool not yet identified for this token \u2014 Photon will open to the homepage instead of this token's page.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
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

@Composable
private fun JsonListSection(title: String, json: String?, bullet: String) {
    val values = parseJsonList(json)
    if (values.isNotEmpty()) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        values.forEach { Text("$bullet $it", style = MaterialTheme.typography.bodySmall) }
    }
}

private fun parseJsonList(json: String?): List<String> = json?.let {
    runCatching {
        val array = JSONArray(it)
        (0 until array.length()).map { index -> array.getString(index) }
    }.getOrDefault(emptyList())
} ?: emptyList()

private fun formatAge(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        else -> "${seconds / 3600}h"
    }
}

@Composable
private fun StateBadge(state: String) {
    val color = when (state) {
        "STRONG_MOMENTUM", "EXTREME_MOMENTUM", "EARLY_MOMENTUM" -> BuyGreen
        "INVALIDATED", "AVOID", "COLLAPSING" -> SellRed
        else -> MaterialTheme.colorScheme.secondary
    }
    AssistChip(onClick = {}, label = { Text(state, color = color) })
}
