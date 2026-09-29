package com.solanasignal.app.ui.paper

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solanasignal.app.data.room.entities.TokenEntity
import com.solanasignal.app.domain.paper.PaperTradeResult
import com.solanasignal.app.ui.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun PaperTerminalScreen(vm: AppViewModel) {
    val tokens by vm.tokens.collectAsState()
    val portfolio by vm.paperPortfolio.collectAsState()
    val positions by vm.paperPositions.collectAsState()
    val trades by vm.paperTrades.collectAsState()
    val watchlist by vm.paperWatchlist.collectAsState()
    val analytics by vm.paperAnalytics.collectAsState()
    val autoStatus by vm.autoPaperStatus.collectAsState()
    var selectedMint by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("25") }
    var search by remember { mutableStateOf("") }
    var sortByMomentum by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val watchMints = remember(watchlist) { watchlist.map { it.mint }.toSet() }
    val filtered = remember(tokens, search, sortByMomentum) {
        tokens.filter { token ->
            search.isBlank() || token.symbol.orEmpty().contains(search, true) || token.name.orEmpty().contains(search, true) || token.mint.contains(search, true)
        }.let { values ->
            if (sortByMomentum) values.sortedByDescending { it.momentumScore ?: -1 } else values.sortedByDescending { it.marketCapUsd ?: 0.0 }
        }.take(50)
    }
    val selected = filtered.firstOrNull { it.mint == selectedMint } ?: filtered.firstOrNull()

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("SOLANA SIGNAL • DEMO TERMINAL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("PAPER TRADING • SIMULATION ONLY", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("No wallet connection • no private keys • no blockchain transactions", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("Cash", "$%.2f".format(portfolio?.cashUsd ?: analytics.cashUsd))
                        Metric("Realized", "%+.2f".format(analytics.realizedPnlUsd))
                        Metric("Unrealized", "%+.2f".format(analytics.unrealizedPnlUsd))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Closed trades ${analytics.closedTrades}", style = MaterialTheme.typography.labelSmall)
                        Text("Win rate ${analytics.winRate?.let { "%.0f%%".format(it * 100) } ?: "UNKNOWN"}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("AUTO PAPER-TRADING", fontWeight = FontWeight.Bold)
                            Text(if (autoStatus.enabled) "RUNNING • simulation only" else "OFF • manual approval mode", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = autoStatus.enabled, onCheckedChange = vm::setAutoPaperTrading)
                    }
                    Text("Entry: Momentum ≥ 80 • Risk ≤ 40 • Confidence ≥ 70 • rising MC • liquidity ≥ $5k", style = MaterialTheme.typography.labelSmall)
                    Text("TP +30% • SL -15% • cooldown 120s • max 5 positions", style = MaterialTheme.typography.labelSmall)
                    Text("${autoStatus.state}: ${autoStatus.lastDecision}", style = MaterialTheme.typography.bodySmall)
                    Text("Auto entries ${autoStatus.entries} • auto exits ${autoStatus.exits}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            OutlinedTextField(search, { search = it }, label = { Text("Search symbol, name, or mint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(sortByMomentum, { sortByMomentum = true }, label = { Text("Momentum") })
                FilterChip(!sortByMomentum, { sortByMomentum = false }, label = { Text("Market Cap") })
                Text("${filtered.size} tokens", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 12.dp))
            }
        }
        item { Text("DISCOVERY / WATCHLIST", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        items(filtered, key = { it.mint }) { token ->
            TokenRow(token, selected?.mint == token.mint, token.mint in watchMints,
                onClick = { selectedMint = token.mint },
                onWatch = { if (token.mint in watchMints) vm.removeFromWatchlist(token.mint) else vm.addToWatchlist(token.mint) })
        }
        selected?.let { token ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("TOKEN TERMINAL • ${token.symbol ?: token.mint.take(8)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(token.mint, style = MaterialTheme.typography.labelSmall)
                        Metric("MC", token.marketCapUsd?.let { "$%,.0f".format(it) } ?: "UNKNOWN")
                        Metric("Liquidity", token.liquidityUsd?.let { "$%,.0f".format(it) } ?: "UNKNOWN")
                        Metric("Price", token.lastPriceUsd?.let { "%.10f".format(it) } ?: "UNKNOWN")
                        Metric("Momentum / Risk", "${token.momentumScore ?: "?"} / ${token.manipulationRiskScore ?: "?"}")
                        Metric("MC trend", token.marketCapVelocityPct?.let { "%+.2f%%/min".format(it) } ?: "UNKNOWN")
                        OutlinedTextField(amount, { amount = it }, label = { Text("Paper BUY amount (USDC)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { scope.launch { status = when (val result = vm.paperBuy(token.mint, amount.toDoubleOrNull() ?: 0.0)) { is PaperTradeResult.Success -> "PAPER BUY filled ${result.quantity} @ ${result.fillPriceUsd}"; is PaperTradeResult.Rejected -> result.reason } } }) { Text("PAPER BUY") }
                            Button(onClick = { scope.launch { val position = positions.firstOrNull { it.mint == token.mint }; status = when (val result = vm.paperSell(token.mint, position?.quantity ?: 0.0)) { is PaperTradeResult.Success -> "PAPER SELL filled; PnL recorded"; is PaperTradeResult.Rejected -> result.reason } } }) { Text("PAPER SELL") }
                        }
                        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        item { Text("OPEN POSITIONS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        items(positions, key = { it.mint }) { position ->
            val current = tokens.firstOrNull { it.mint == position.mint }?.lastPriceUsd ?: position.currentPriceUsd
            val pnl = (current - position.averageEntryPriceUsd) * position.quantity
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) { Text(position.symbol ?: position.mint.take(8), fontWeight = FontWeight.Bold); Text("Qty ${position.quantity} • Entry ${position.averageEntryPriceUsd} • PnL %+.4f".format(pnl)) } }
        }
        item { Text("LIVE PAPER TRADE TAPE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        items(trades.take(15), key = { it.id }) { trade ->
            Text("${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(trade.timestamp))}  ${trade.side}  ${trade.symbol ?: trade.mint.take(6)}  fill ${trade.fillPriceUsd}  fee ${trade.feeUsd}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TokenRow(token: TokenEntity, selected: Boolean, watched: Boolean, onClick: () -> Unit, onWatch: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected, onClick, label = { Text("${token.symbol ?: token.mint.take(6)}  MC ${token.marketCapUsd?.let { "%.0f".format(it) } ?: "?"}  M ${token.momentumScore ?: "?"}  R ${token.manipulationRiskScore ?: "?"}") }, modifier = Modifier.weight(1f))
            TextButton(onClick = onWatch) { Text(if (watched) "★" else "☆") }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column { Text(label, style = MaterialTheme.typography.labelSmall); Text(value, fontWeight = FontWeight.Medium) }
}
