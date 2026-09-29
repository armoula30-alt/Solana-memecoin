package com.solanasignal.app.ui.paper

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
    var selectedMint by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("25") }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val selected = tokens.firstOrNull { it.mint == selectedMint } ?: tokens.firstOrNull()

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("SOLANA SIGNAL • DEMO TERMINAL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("PAPER TRADING • SIMULATION ONLY", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("No wallet connection • no private keys • no blockchain transactions", style = MaterialTheme.typography.bodySmall)
                    Text("Cash: $%.2f".format(portfolio?.cashUsd ?: 1_000.0))
                    Text("Realized PnL: %+.2f".format(portfolio?.realizedPnlUsd ?: 0.0))
                }
            }
        }
        item {
            Text("DISCOVERY FEED", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(amount, { amount = it }, label = { Text("Paper buy amount (USDC)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        items(tokens.take(50), key = { it.mint }) { token ->
            TokenRow(token, selected?.mint == token.mint) { selectedMint = token.mint }
        }
        selected?.let { token ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("TOKEN TERMINAL • ${token.symbol ?: token.mint.take(8)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Metric("MC", token.marketCapUsd?.let { "$%,.0f".format(it) } ?: "UNKNOWN")
                        Metric("Liquidity", token.liquidityUsd?.let { "$%,.0f".format(it) } ?: "UNKNOWN")
                        Metric("Price", token.lastPriceUsd?.let { "%.10f".format(it) } ?: "UNKNOWN")
                        Metric("Momentum / Risk", "${token.momentumScore ?: "?"} / ${token.manipulationRiskScore ?: "?"}")
                        Metric("MC velocity", token.marketCapVelocityPct?.let { "%+.2f%%/min".format(it) } ?: "UNKNOWN")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    status = when (val result = vm.paperBuy(token.mint, amount.toDoubleOrNull() ?: 0.0)) {
                                        is PaperTradeResult.Success -> "PAPER BUY filled ${result.quantity} ${token.symbol ?: "tokens"} @ ${result.fillPriceUsd}"
                                        is PaperTradeResult.Rejected -> result.reason
                                    }
                                }
                            }) { Text("PAPER BUY") }
                            Button(onClick = {
                                scope.launch {
                                    val position = positions.firstOrNull { it.mint == token.mint }
                                    status = when (val result = vm.paperSell(token.mint, position?.quantity ?: 0.0)) {
                                        is PaperTradeResult.Success -> "PAPER SELL filled; PnL recorded"
                                        is PaperTradeResult.Rejected -> result.reason
                                    }
                                }
                            }) { Text("PAPER SELL") }
                        }
                        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        item {
            Text("OPEN POSITIONS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            positions.forEach { position ->
                Text("${position.symbol ?: position.mint.take(8)} • ${position.quantity} • entry ${position.averageEntryPriceUsd}")
            }
            Spacer(Modifier.height(4.dp))
            Text("PAPER TRADE HISTORY: ${trades.size} simulated fills", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TokenRow(token: TokenEntity, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = {
        Text("${token.symbol ?: token.mint.take(6)}  MC ${token.marketCapUsd?.let { "%.0f".format(it) } ?: "?"}  M ${token.momentumScore ?: "?"}  R ${token.manipulationRiskScore ?: "?"}")
    }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
