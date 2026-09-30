package com.solanasignal.app.data.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryModelsTest {
    @Test fun secretPropertiesAreRedactedInStructuredJson() {
        val json = DiagnosticJson.stringify(mapOf("apiKey" to "top-secret", "nested" to mapOf("private_key" to "seed-value")))
        assertTrue(json.contains("[REDACTED]"))
        assertTrue(!json.contains("top-secret"))
        assertTrue(!json.contains("seed-value"))
    }

    @Test fun rawUrlApiKeyIsRedactedAndLongFramesAreBounded() {
        val safe = DiagnosticJson.safeRawFrame("wss://host/ws?api-key=supersecret&x=1")
        assertTrue(!safe.contains("supersecret"))
        assertTrue(safe.contains("[REDACTED]"))
        assertTrue(DiagnosticJson.safeRawFrame("x".repeat(20_000)).length < 8_300)
    }

    @Test fun publicWalletIdentitiesAreRedactedFromLogsAndRawFrames() {
        val address = "11111111111111111111111111111111"
        val structured = DiagnosticJson.stringify(mapOf("trader" to address, "creator" to address))
        val raw = DiagnosticJson.safeRawFrame("{\"traderPublicKey\":\"$address\",\"txType\":\"buy\"}")
        assertTrue(!structured.contains(address))
        assertTrue(!raw.contains(address))
        assertTrue(raw.contains("[REDACTED]"))
    }

    @Test fun jsonEscapesControlCharactersAndNonFiniteValuesBecomeNull() {
        val json = DiagnosticJson.stringify(mapOf("message" to "quote \" slash \\\n", "value" to Double.NaN))
        assertTrue(json.contains("\\n"))
        assertTrue(json.contains("\"value\":null"))
        assertEquals('{', json.first())
        assertEquals('}', json.last())
    }
}
