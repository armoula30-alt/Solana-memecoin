package com.solanasignal.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.solanasignal.app.data.settings.BatteryMode
import com.solanasignal.app.data.settings.FilterConfig
import com.solanasignal.app.data.settings.RetentionPolicy
import com.solanasignal.app.ui.AppViewModel

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val apiKeyConfigured by vm.settings.apiKeyConfigured.collectAsState()
    val codeCraftConfigured by vm.settings.codeCraftConfigured.collectAsState()
    val codeCraftModel by vm.settings.codeCraftModel.collectAsState()
    val mockMode by vm.settings.mockMode.collectAsState()
    val liveTradeStreaming by vm.settings.liveTradeStreamingEnabled.collectAsState()
    val batteryMode by vm.settings.batteryMode.collectAsState()
    val filters by vm.settings.filterConfig.collectAsState()
    val retention by vm.settings.retentionPolicy.collectAsState()

    var apiKeyInput by remember { mutableStateOf("") }
    var showKeyField by remember { mutableStateOf(!apiKeyConfigured) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }

        item {
            SettingsCard("PumpPortal API Key") {
                Text(
                    if (apiKeyConfigured) "Status: CONFIGURED (stored encrypted on-device)" else "Status: NOT SET",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Token discovery is free. Live token-trade streaming is a separate, metered PumpPortal feature and is OFF by default.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Official rate: 0.01 SOL per 10,000 received trade events. Requires an API key linked to a wallet funded with at least 0.02 SOL. Fees depend on actual event volume; the app does not estimate or pay a fixed amount.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Enable metered live trade stream", fontWeight = FontWeight.Medium)
                        Text("Subscribes only to actively tracked tokens while scanning.", style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(
                        checked = liveTradeStreaming,
                        enabled = apiKeyConfigured,
                        onCheckedChange = vm::setLiveTradeStreamingEnabled
                    )
                }
                if (!apiKeyConfigured) Text("Set a PumpPortal API key before enabling live trades.", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                if (showKeyField) {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (apiKeyInput.isNotBlank()) {
                            vm.setApiKey(apiKeyInput)
                            apiKeyInput = ""
                            showKeyField = false
                        }
                    }) { Text("Save Key") }
                } else {
                    Row {
                        OutlinedButton(onClick = { showKeyField = true }) { Text("Replace Key") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { vm.clearApiKey() }) { Text("Clear Key") }
                    }
                }
            }
        }

        item {
            SettingsCard("CodeCraft AI Agent") {
                Text(
                    if (codeCraftConfigured) "Status: CONFIGURED (stored encrypted on-device)"
                    else "Status: DISABLED — optional",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Only high-scoring DexScreener candidates are sent to CodeCraft for a second opinion. The AI cannot trade or sign transactions.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                var aiKeyInput by remember { mutableStateOf("") }
                var modelInput by remember(codeCraftModel) { mutableStateOf(codeCraftModel) }
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text("Model (for example claude-opus-4.8)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = aiKeyInput,
                    onValueChange = { aiKeyInput = it },
                    label = { Text(if (codeCraftConfigured) "Replace CodeCraft API key" else "CodeCraft API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = {
                        vm.setCodeCraftModel(modelInput)
                        if (aiKeyInput.isNotBlank()) {
                            vm.setCodeCraftKey(aiKeyInput)
                            aiKeyInput = ""
                        }
                    }) { Text("Save AI Settings") }
                    if (codeCraftConfigured) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { vm.clearCodeCraftKey() }) { Text("Disable AI") }
                    }
                }
            }
        }

        item {
            SettingsCard("Mock Mode") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Simulate tokens/trades (no live connection)")
                    Switch(checked = mockMode, onCheckedChange = { vm.setMockMode(it) })
                }
            }
        }

        item {
            SettingsCard("Battery Mode") {
                BatteryMode.values().forEach { mode ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(mode.name.replace("_", " "))
                        RadioButton(selected = batteryMode == mode, onClick = { vm.setBatteryMode(mode) })
                    }
                }
            }
        }

        item {
            SettingsCard("Initial Filters") {
                LabeledSlider("Max Token Age (s)", filters.maxTokenAgeSeconds.toFloat(), 30f, 900f) {
                    vm.updateFilters(filters.copy(maxTokenAgeSeconds = it.toInt()))
                }
                LabeledSlider("Min Market Cap ($)", filters.minMarketCapUsd.toFloat(), 1000f, 100000f) {
                    vm.updateFilters(filters.copy(minMarketCapUsd = it.toDouble()))
                }
                LabeledSlider("Min Score for BUY", filters.minScoreForBuy.toFloat(), 50f, 100f) {
                    vm.updateFilters(filters.copy(minScoreForBuy = it.toInt()))
                }
                LabeledSlider("Watch Score Floor", filters.watchScoreFloor.toFloat(), 30f, 90f) {
                    vm.updateFilters(filters.copy(watchScoreFloor = it.toInt()))
                }
                LabeledSlider("Buy Cooldown (s)", filters.buySignalCooldownSeconds.toFloat(), 10f, 600f) {
                    vm.updateFilters(filters.copy(buySignalCooldownSeconds = it.toInt()))
                }
                LabeledSlider("Sell Cooldown (s)", filters.sellSignalCooldownSeconds.toFloat(), 10f, 600f) {
                    vm.updateFilters(filters.copy(sellSignalCooldownSeconds = it.toInt()))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Saved automatically. New DexScreener evaluations use these values immediately; existing signals are not recalculated.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item {
            SettingsCard("Data Retention") {
                RetentionPolicy.values().forEach { policy ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(policy.days?.let { "$it days" } ?: "Unlimited")
                        RadioButton(selected = retention == policy, onClick = { vm.setRetention(policy) })
                    }
                }
            }
        }

        item {
            Text(
                "Solana Signal does not hold a wallet, private key, or seed phrase, and never signs or " +
                    "broadcasts transactions. It only detects, analyzes, scores, and alerts. All trades are " +
                    "executed manually by you in Photon.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column {
        Text("$label: ${value.toInt()}", style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onChange, valueRange = min..max)
    }
}
