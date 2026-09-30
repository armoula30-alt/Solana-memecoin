package com.solanasignal.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.solanasignal.app.data.telemetry.MarketFeedProvider

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

    private val _codeCraftConfigured = MutableStateFlow(getCodeCraftKeyOrNull() != null)
    val codeCraftConfigured: StateFlow<Boolean> = _codeCraftConfigured.asStateFlow()

    private val _codeCraftModel = MutableStateFlow(
        securePrefs.getString(KEY_CODECRAFT_MODEL, DEFAULT_CODECRAFT_MODEL) ?: DEFAULT_CODECRAFT_MODEL
    )
    val codeCraftModel: StateFlow<String> = _codeCraftModel.asStateFlow()

    private val _filterConfig = MutableStateFlow(loadFilterConfig())
    val filterConfig: StateFlow<FilterConfig> = _filterConfig.asStateFlow()

    private val _scoreWeights = MutableStateFlow(loadScoreWeights())
    val scoreWeights: StateFlow<ScoreWeights> = _scoreWeights.asStateFlow()

    private val _engineConfig = MutableStateFlow(loadEngineConfig())
    val engineConfig: StateFlow<EngineConfig> = _engineConfig.asStateFlow()

    private val _batteryMode = MutableStateFlow(BatteryMode.BALANCED)
    val batteryMode: StateFlow<BatteryMode> = _batteryMode.asStateFlow()

    private val _mockMode = MutableStateFlow(false)
    val mockMode: StateFlow<Boolean> = _mockMode.asStateFlow()

    private val _liveTradeStreamingEnabled = MutableStateFlow(
        securePrefs.getBoolean(KEY_LIVE_TRADE_STREAMING, false)
    )
    val liveTradeStreamingEnabled: StateFlow<Boolean> = _liveTradeStreamingEnabled.asStateFlow()

    private val _marketFeedProvider = MutableStateFlow(
        runCatching { MarketFeedProvider.valueOf(securePrefs.getString(KEY_MARKET_FEED_PROVIDER, MarketFeedProvider.PUMPPORTAL.name) ?: MarketFeedProvider.PUMPPORTAL.name) }
            .getOrDefault(MarketFeedProvider.PUMPPORTAL)
    )
    val marketFeedProvider: StateFlow<MarketFeedProvider> = _marketFeedProvider.asStateFlow()

    private val _traceLoggingEnabled = MutableStateFlow(securePrefs.getBoolean(KEY_TRACE_LOGGING, false))
    val traceLoggingEnabled: StateFlow<Boolean> = _traceLoggingEnabled.asStateFlow()

    private val _retentionPolicy = MutableStateFlow(RetentionPolicy.THIRTY)
    val retentionPolicy: StateFlow<RetentionPolicy> = _retentionPolicy.asStateFlow()

    fun setApiKey(key: String) {
        // Never logged. Stored only in the Keystore-backed encrypted prefs.
        val normalized = key.trim()
        if (getApiKeyOrNull() != normalized) setLiveTradeStreamingEnabled(false)
        securePrefs.edit().putString(KEY_API_KEY, normalized).apply()
        _apiKeyConfigured.value = normalized.isNotBlank()
    }

    fun clearApiKey() {
        securePrefs.edit().remove(KEY_API_KEY).apply()
        _apiKeyConfigured.value = false
        setLiveTradeStreamingEnabled(false)
    }

    fun setCodeCraftKey(key: String) {
        securePrefs.edit().putString(KEY_CODECRAFT_KEY, key.trim()).apply()
        _codeCraftConfigured.value = key.isNotBlank()
    }

    fun clearCodeCraftKey() {
        securePrefs.edit().remove(KEY_CODECRAFT_KEY).apply()
        _codeCraftConfigured.value = false
    }

    fun getCodeCraftKeyOrNull(): String? =
        securePrefs.getString(KEY_CODECRAFT_KEY, null)?.takeIf { it.isNotBlank() }

    fun setCodeCraftModel(model: String) {
        val value = model.trim().ifBlank { DEFAULT_CODECRAFT_MODEL }
        securePrefs.edit().putString(KEY_CODECRAFT_MODEL, value).apply()
        _codeCraftModel.value = value
    }

    /** Returns the raw key only to the WebSocket manager building the connection URL. Never log this value. */
    fun getApiKeyOrNull(): String? = securePrefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }

    private fun hasApiKey(): Boolean = getApiKeyOrNull() != null

    fun updateFilters(config: FilterConfig) {
        val normalized = config.copy(
            maxTokenAgeSeconds = config.maxTokenAgeSeconds.coerceAtLeast(1),
            minMarketCapUsd = config.minMarketCapUsd.coerceAtLeast(0.0),
            minScoreForBuy = config.minScoreForBuy.coerceIn(0, 100),
            watchScoreFloor = config.watchScoreFloor.coerceIn(0, 100),
            buySignalCooldownSeconds = config.buySignalCooldownSeconds.coerceAtLeast(0),
            sellSignalCooldownSeconds = config.sellSignalCooldownSeconds.coerceAtLeast(0),
            resignalScoreDelta = config.resignalScoreDelta.coerceAtLeast(0)
        )
        securePrefs.edit()
            .putInt(KEY_FILTER_MAX_AGE, normalized.maxTokenAgeSeconds)
            .putString(KEY_FILTER_MIN_MC, normalized.minMarketCapUsd.toString())
            .putInt(KEY_FILTER_MIN_BUY_SCORE, normalized.minScoreForBuy)
            .putInt(KEY_FILTER_WATCH_FLOOR, normalized.watchScoreFloor)
            .putInt(KEY_FILTER_BUY_COOLDOWN, normalized.buySignalCooldownSeconds)
            .putInt(KEY_FILTER_SELL_COOLDOWN, normalized.sellSignalCooldownSeconds)
            .putBoolean(KEY_FILTER_BUYERS, normalized.requireBuyersGtSellers)
            .putBoolean(KEY_FILTER_VOLUME, normalized.requireBuyVolumeGtSellVolume)
            .putInt(KEY_FILTER_RESIGNAL_DELTA, normalized.resignalScoreDelta)
            .apply()
        _filterConfig.value = normalized
    }
    fun updateWeights(weights: ScoreWeights) {
        securePrefs.edit()
            .putString(KEY_WEIGHT_BUYER, weights.buyerPressure.toString())
            .putString(KEY_WEIGHT_VOLUME, weights.volumePressure.toString())
            .putString(KEY_WEIGHT_VELOCITY, weights.volumeVelocity.toString())
            .putString(KEY_WEIGHT_PRICE, weights.priceMomentum.toString())
            .putString(KEY_WEIGHT_LIQUIDITY, weights.liquidity.toString())
            .putString(KEY_WEIGHT_HOLDERS, weights.holderDistribution.toString())
            .putString(KEY_WEIGHT_SAFETY, weights.safety.toString())
            .apply()
        _scoreWeights.value = weights
    }
    fun updateEngineConfig(config: EngineConfig) {
        securePrefs.edit()
            .putInt(KEY_ENGINE_MIN_OBSERVATIONS, config.minimumObservationCount)
            .putString(KEY_ENGINE_MIN_LIQUIDITY, config.minimumLiquidityUsd.toString())
            .putInt(KEY_ENGINE_FRESHNESS, config.dataFreshnessSeconds)
            .putString(KEY_ENGINE_SPIKE_SHARE, config.antiSpikeLargestTradeShare.toString())
            .putInt(KEY_ENGINE_SPIKE_BUYERS, config.antiSpikeMinimumUniqueBuyers)
            .apply()
        _engineConfig.value = config
    }
    fun setBatteryMode(mode: BatteryMode) { _batteryMode.value = mode }
    fun setMockMode(enabled: Boolean) {
        _mockMode.value = enabled
        if (enabled) setLiveTradeStreamingEnabled(false)
    }
    fun setLiveTradeStreamingEnabled(enabled: Boolean) {
        val credentialsReady = !_marketFeedProvider.value.requiresApiKey || hasApiKey()
        val allowed = enabled && credentialsReady && !_mockMode.value
        securePrefs.edit().putBoolean(KEY_LIVE_TRADE_STREAMING, allowed).apply()
        _liveTradeStreamingEnabled.value = allowed
    }
    fun setMarketFeedProvider(provider: MarketFeedProvider) {
        if (_marketFeedProvider.value == provider) return
        // A provider change invalidates the previous explicit metered-stream consent.
        setLiveTradeStreamingEnabled(false)
        securePrefs.edit().putString(KEY_MARKET_FEED_PROVIDER, provider.name).apply()
        _marketFeedProvider.value = provider
    }
    fun setTraceLoggingEnabled(enabled: Boolean) {
        securePrefs.edit().putBoolean(KEY_TRACE_LOGGING, enabled).apply()
        _traceLoggingEnabled.value = enabled
    }
    fun setRetentionPolicy(policy: RetentionPolicy) { _retentionPolicy.value = policy }

    private fun loadFilterConfig(): FilterConfig = FilterConfig(
        maxTokenAgeSeconds = securePrefs.getInt(KEY_FILTER_MAX_AGE, 300),
        minMarketCapUsd = securePrefs.getString(KEY_FILTER_MIN_MC, "10000.0")?.toDoubleOrNull() ?: 10_000.0,
        minScoreForBuy = securePrefs.getInt(KEY_FILTER_MIN_BUY_SCORE, 80),
        watchScoreFloor = securePrefs.getInt(KEY_FILTER_WATCH_FLOOR, 70),
        buySignalCooldownSeconds = securePrefs.getInt(KEY_FILTER_BUY_COOLDOWN, 120),
        sellSignalCooldownSeconds = securePrefs.getInt(KEY_FILTER_SELL_COOLDOWN, 60),
        requireBuyersGtSellers = securePrefs.getBoolean(KEY_FILTER_BUYERS, true),
        requireBuyVolumeGtSellVolume = securePrefs.getBoolean(KEY_FILTER_VOLUME, true),
        resignalScoreDelta = securePrefs.getInt(KEY_FILTER_RESIGNAL_DELTA, 8)
    )

    private fun loadScoreWeights(): ScoreWeights = ScoreWeights(
        buyerPressure = securePrefs.getString(KEY_WEIGHT_BUYER, "0.25")?.toDoubleOrNull() ?: 0.25,
        volumePressure = securePrefs.getString(KEY_WEIGHT_VOLUME, "0.25")?.toDoubleOrNull() ?: 0.25,
        volumeVelocity = securePrefs.getString(KEY_WEIGHT_VELOCITY, "0.15")?.toDoubleOrNull() ?: 0.15,
        priceMomentum = securePrefs.getString(KEY_WEIGHT_PRICE, "0.10")?.toDoubleOrNull() ?: 0.10,
        liquidity = securePrefs.getString(KEY_WEIGHT_LIQUIDITY, "0.10")?.toDoubleOrNull() ?: 0.10,
        holderDistribution = securePrefs.getString(KEY_WEIGHT_HOLDERS, "0.10")?.toDoubleOrNull() ?: 0.10,
        safety = securePrefs.getString(KEY_WEIGHT_SAFETY, "0.05")?.toDoubleOrNull() ?: 0.05
    )

    private fun loadEngineConfig(): EngineConfig = EngineConfig(
        minimumObservationCount = securePrefs.getInt(KEY_ENGINE_MIN_OBSERVATIONS, 3),
        minimumLiquidityUsd = securePrefs.getString(KEY_ENGINE_MIN_LIQUIDITY, "5000.0")?.toDoubleOrNull() ?: 5_000.0,
        dataFreshnessSeconds = securePrefs.getInt(KEY_ENGINE_FRESHNESS, 30),
        antiSpikeLargestTradeShare = securePrefs.getString(KEY_ENGINE_SPIKE_SHARE, "0.60")?.toDoubleOrNull() ?: 0.60,
        antiSpikeMinimumUniqueBuyers = securePrefs.getInt(KEY_ENGINE_SPIKE_BUYERS, 3)
    )

    companion object {
        private const val KEY_API_KEY = "pumpportal_api_key"
        private const val KEY_CODECRAFT_KEY = "codecraft_api_key"
        private const val KEY_CODECRAFT_MODEL = "codecraft_model"
        private const val KEY_FILTER_MAX_AGE = "filter_max_token_age_seconds"
        private const val KEY_FILTER_MIN_MC = "filter_min_market_cap_usd"
        private const val KEY_FILTER_MIN_BUY_SCORE = "filter_min_score_for_buy"
        private const val KEY_FILTER_WATCH_FLOOR = "filter_watch_score_floor"
        private const val KEY_FILTER_BUY_COOLDOWN = "filter_buy_cooldown_seconds"
        private const val KEY_FILTER_SELL_COOLDOWN = "filter_sell_cooldown_seconds"
        private const val KEY_FILTER_BUYERS = "filter_require_buyers"
        private const val KEY_FILTER_VOLUME = "filter_require_buy_volume"
        private const val KEY_FILTER_RESIGNAL_DELTA = "filter_resignal_delta"
        private const val KEY_WEIGHT_BUYER = "weight_buyer_pressure"
        private const val KEY_WEIGHT_VOLUME = "weight_volume_pressure"
        private const val KEY_WEIGHT_VELOCITY = "weight_volume_velocity"
        private const val KEY_WEIGHT_PRICE = "weight_price_momentum"
        private const val KEY_WEIGHT_LIQUIDITY = "weight_liquidity"
        private const val KEY_WEIGHT_HOLDERS = "weight_holder_distribution"
        private const val KEY_WEIGHT_SAFETY = "weight_safety"
        private const val KEY_ENGINE_MIN_OBSERVATIONS = "engine_min_observations"
        private const val KEY_ENGINE_MIN_LIQUIDITY = "engine_min_liquidity_usd"
        private const val KEY_ENGINE_FRESHNESS = "engine_data_freshness_seconds"
        private const val KEY_ENGINE_SPIKE_SHARE = "engine_anti_spike_largest_share"
        private const val KEY_ENGINE_SPIKE_BUYERS = "engine_anti_spike_min_buyers"
        private const val KEY_LIVE_TRADE_STREAMING = "live_trade_streaming_enabled"
        private const val KEY_MARKET_FEED_PROVIDER = "market_feed_provider"
        private const val KEY_TRACE_LOGGING = "diagnostic_trace_logging_enabled"
        const val DEFAULT_CODECRAFT_MODEL = "claude-opus-4.8"

        @Volatile private var instance: SettingsRepository? = null
        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
