package com.solanasignal.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BatteryMode { PERFORMANCE, BALANCED, BATTERY_SAVER }
enum class RetentionPolicy(val days: Int?) { SEVEN(7), THIRTY(30), NINETY(90), UNLIMITED(null) }

/**
 * Configurable hard filters + scoring thresholds (spec sections 10, 20, 21, 22).
 * All defaults match the spec; every value is user-editable in Settings.
 */
data class FilterConfig(
    val maxTokenAgeSeconds: Int = 300,
    val minMarketCapUsd: Double = 10_000.0,
    val requireBuyersGtSellers: Boolean = true,
    val requireBuyVolumeGtSellVolume: Boolean = true,
    val minScoreForBuy: Int = 80,
    val watchScoreFloor: Int = 70,
    val buySignalCooldownSeconds: Int = 120,
    val sellSignalCooldownSeconds: Int = 60,
    val resignalScoreDelta: Int = 8
)

data class ScoreWeights(
    val buyerPressure: Double = 0.25,
    val volumePressure: Double = 0.25,
    val volumeVelocity: Double = 0.15,
    val priceMomentum: Double = 0.10,
    val liquidity: Double = 0.10,
    val holderDistribution: Double = 0.10,
    val safety: Double = 0.05
)

/**
 * SECURITY (spec section 6 / 45): the PumpPortal API key is the only sensitive
 * credential this app holds. It is stored in EncryptedSharedPreferences backed by
 * the Android Keystore, is never logged, never included in crash reports, and is
 * never transmitted anywhere except as part of the official PumpPortal WebSocket
 * URL the user explicitly configured.
 */
class SettingsRepository private constructor(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "solana_signal_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _apiKeyConfigured = MutableStateFlow(hasApiKey())
    val apiKeyConfigured: StateFlow<Boolean> = _apiKeyConfigured.asStateFlow()

    private val _filterConfig = MutableStateFlow(FilterConfig())
    val filterConfig: StateFlow<FilterConfig> = _filterConfig.asStateFlow()

    private val _scoreWeights = MutableStateFlow(ScoreWeights())
    val scoreWeights: StateFlow<ScoreWeights> = _scoreWeights.asStateFlow()

    private val _batteryMode = MutableStateFlow(BatteryMode.BALANCED)
    val batteryMode: StateFlow<BatteryMode> = _batteryMode.asStateFlow()

    private val _mockMode = MutableStateFlow(false)
    val mockMode: StateFlow<Boolean> = _mockMode.asStateFlow()

    private val _retentionPolicy = MutableStateFlow(RetentionPolicy.THIRTY)
    val retentionPolicy: StateFlow<RetentionPolicy> = _retentionPolicy.asStateFlow()

    fun setApiKey(key: String) {
        // Never logged. Stored only in the Keystore-backed encrypted prefs.
        securePrefs.edit().putString(KEY_API_KEY, key.trim()).apply()
        _apiKeyConfigured.value = key.isNotBlank()
    }

    fun clearApiKey() {
        securePrefs.edit().remove(KEY_API_KEY).apply()
        _apiKeyConfigured.value = false
    }

    /** Returns the raw key only to the WebSocket manager building the connection URL. Never log this value. */
    fun getApiKeyOrNull(): String? = securePrefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }

    private fun hasApiKey(): Boolean = getApiKeyOrNull() != null

    fun updateFilters(config: FilterConfig) { _filterConfig.value = config }
    fun updateWeights(weights: ScoreWeights) { _scoreWeights.value = weights }
    fun setBatteryMode(mode: BatteryMode) { _batteryMode.value = mode }
    fun setMockMode(enabled: Boolean) { _mockMode.value = enabled }
    fun setRetentionPolicy(policy: RetentionPolicy) { _retentionPolicy.value = policy }

    companion object {
        private const val KEY_API_KEY = "pumpportal_api_key"

        @Volatile private var instance: SettingsRepository? = null
        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
