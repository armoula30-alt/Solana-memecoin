package com.solanasignal.app.data.pumpdev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PumpDevWebSocketUrlTest {
    @Test
    fun emptyKeyUsesBackwardCompatibleBaseUrl() {
        assertEquals("wss://pumpdev.io/ws", PumpDevWebSocketUrl.build(null))
        assertEquals("wss://pumpdev.io/ws", PumpDevWebSocketUrl.build("  "))
    }

    @Test
    fun configuredKeyUsesAuthenticatedUrl() {
        assertEquals("wss://pumpdev.io/ws?key=abc123", PumpDevWebSocketUrl.build("abc123"))
    }

    @Test
    fun keyIsUrlEncodedSafely() {
        val url = PumpDevWebSocketUrl.build("a+b c&d?e")
        assertEquals("wss://pumpdev.io/ws?key=a%2Bb%20c%26d%3Fe", url)
    }

    @Test
    fun keyNeverAppearsInSanitizedDiagnostics() {
        val key = "secret-value"
        val raw = "WebSocket failed for wss://pumpdev.io/ws?key=$key"
        val sanitized = PumpDevWebSocketUrl.sanitize(raw)
        assertFalse(sanitized.contains(key))
        assertTrue(sanitized.contains("key=[REDACTED]"))
    }

    @Test
    fun keyProviderCanReturnReplacementAndEmptyValue() {
        var key: String? = "old-key"
        assertEquals("wss://pumpdev.io/ws?key=old-key", PumpDevWebSocketUrl.build(key))
        key = "new-key"
        assertEquals("wss://pumpdev.io/ws?key=new-key", PumpDevWebSocketUrl.build(key))
        key = null
        assertEquals("wss://pumpdev.io/ws", PumpDevWebSocketUrl.build(key))
    }
}
