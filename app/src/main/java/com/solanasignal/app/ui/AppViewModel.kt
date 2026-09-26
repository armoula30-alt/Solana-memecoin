package com.solanasignal.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solanasignal.app.data.room.entities.SignalEntity
import com.solanasignal.app.data.room.entities.SystemEventEntity
import com.solanasignal.app.data.room.entities.TokenEntity
import com.solanasignal.app.data.settings.*
import com.solanasignal.app.di.ServiceLocator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()
    val settings: SettingsRepository = ServiceLocator.settings(ctx)
    private val db = ServiceLocator.database(ctx)
    private val orchestrator = ServiceLocator.orchestrator(ctx)

    val running = orchestrator.running
    val connectionState = orchestrator.connectionState
    val solUsdPrice = orchestrator.solUsdPrice
    val reconnectCount = orchestrator.reconnectCount
    val parserErrorCount = orchestrator.parserErrorCount
    val eventsPerSecond = orchestrator.eventsPerSecond
    val trackedSubscriptionCount = orchestrator.trackedSubscriptionCount
    val dexScreenerEnrichedCount = orchestrator.dexScreenerEnrichedCount
    val tradesReceivedCount = orchestrator.tradesReceivedCount

    val tokens: StateFlow<List<TokenEntity>> =
        db.tokenDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val signals: StateFlow<List<SignalEntity>> =
        db.signalDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val systemEvents: StateFlow<List<SystemEventEntity>> =
        db.systemEventDao().observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startScanner() = orchestrator.start()
    fun stopScanner() = orchestrator.stop()

    fun setApiKey(key: String) = settings.setApiKey(key)
    fun clearApiKey() = settings.clearApiKey()
    fun setMockMode(enabled: Boolean) = settings.setMockMode(enabled)
    fun setBatteryMode(mode: BatteryMode) = settings.setBatteryMode(mode)
    fun updateFilters(config: FilterConfig) = settings.updateFilters(config)
    fun updateWeights(weights: ScoreWeights) = settings.updateWeights(weights)
    fun setRetention(policy: RetentionPolicy) = settings.setRetentionPolicy(policy)

    suspend fun signalCountToday(): Int {
        val since = todayStartMs()
        return db.signalDao().countSince(since)
    }

    private fun todayStartMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
