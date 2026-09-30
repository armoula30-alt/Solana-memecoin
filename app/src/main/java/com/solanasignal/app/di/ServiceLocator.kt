package com.solanasignal.app.di

import android.content.Context
import com.solanasignal.app.data.room.AppDatabase
import com.solanasignal.app.data.market.LiveMarketStateRepository
import com.solanasignal.app.data.settings.SettingsRepository
import com.solanasignal.app.domain.scanner.ScannerOrchestrator
import com.solanasignal.app.data.telemetry.StructuredTelemetryLogger

/**
 * Minimal hand-rolled DI container to keep the project dependency-light
 * (no Hilt/Dagger required to build). Swap for Hilt later if desired.
 */
object ServiceLocator {
    @Volatile private var appContext: Context? = null
    @Volatile private var orchestratorInstance: ScannerOrchestrator? = null
    @Volatile private var marketStateInstance: LiveMarketStateRepository? = null
    @Volatile private var telemetryInstance: StructuredTelemetryLogger? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        AppDatabase.get(context)
        SettingsRepository.get(context)
    }

    fun settings(context: Context): SettingsRepository = SettingsRepository.get(context)
    fun database(context: Context): AppDatabase = AppDatabase.get(context)

    fun telemetry(context: Context): StructuredTelemetryLogger = telemetryInstance ?: synchronized(this) {
        telemetryInstance ?: StructuredTelemetryLogger(database(context.applicationContext)).also { telemetryInstance = it }
    }

    fun marketState(context: Context): LiveMarketStateRepository = marketStateInstance ?: synchronized(this) {
        marketStateInstance ?: LiveMarketStateRepository().also { marketStateInstance = it }
    }

    fun orchestrator(context: Context): ScannerOrchestrator =
        orchestratorInstance ?: synchronized(this) {
            orchestratorInstance ?: ScannerOrchestrator(
                context.applicationContext,
                marketStates = marketState(context.applicationContext),
                telemetry = telemetry(context.applicationContext)
            ).also { orchestratorInstance = it }
        }
}
