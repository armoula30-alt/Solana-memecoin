package com.solanasignal.app.data.pumpdev

import org.junit.Assert.assertEquals
import org.junit.Test

class PumpDevWebSocketManagerTest {
    @Test
    fun sameMintCreatesOneActiveSubscription() {
        val manager = PumpDevWebSocketManager(
            onMessage = { _, _ -> },
            onSystemEvent = { _, _ -> }
        )
        manager.subscribeTokenTrade("MINT")
        manager.subscribeTokenTrade("MINT")
        assertEquals(1, manager.activeSubscriptions.value)
        manager.stop()
    }
}
