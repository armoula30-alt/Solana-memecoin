package com.solanasignal.app.data.pumpdev

import com.solanasignal.app.data.pumpportal.TradeSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PumpDevParserTest {
    @Test
    fun parsesBuyWithPumpFunFields() {
        val event = PumpDevParser.parseTrade(
            """{"txType":"buy","mint":"MINT","signature":"SIG1","traderPublicKey":"TRADER1","tokenAmount":1000,"solAmount":0.5,"marketCapSol":40.5,"vSolInBondingCurve":35,"vTokensInBondingCurve":900000000}""",
            1_000L
        )
        assertNotNull(event)
        assertEquals("MINT", event!!.mint)
        assertEquals("SIG1", event.signature)
        assertEquals("TRADER1", event.trader)
        assertEquals(TradeSide.BUY, event.side)
        assertEquals(1000.0, event.tokenAmount!!, 0.0)
        assertEquals(0.5, event.solAmount!!, 0.0)
        assertEquals(40.5, event.marketCapSol!!, 0.0)
    }

    @Test
    fun parsesSellWithPumpSwapMetadataWithoutFabricatingSolAmount() {
        val event = PumpDevParser.parseTrade(
            """{"txType":"sell","mint":"MINT2","signature":"SIG2","traderPublicKey":"TRADER2","tokenAmount":2000,"quoteAmount":25,"marketCapQuote":410.83,"source":"pumpswap","pool":"POOL2","quoteMint":"USDC","quoteContextResolved":false}""",
            2_000L
        )
        assertNotNull(event)
        assertEquals(TradeSide.SELL, event!!.side)
        assertEquals(25.0, event.quoteAmount!!, 0.0)
        assertEquals(410.83, event.marketCapQuote!!, 0.0)
        assertEquals("pumpswap", event.source)
        assertEquals("POOL2", event.pool)
        assertNull(event.solAmount)
    }

    @Test
    fun signatureIsThePrimaryDeduplicationKey() {
        val event = PumpDevParser.parseTrade(
            """{"txType":"buy","mint":"MINT","signature":"SAME","traderPublicKey":"TRADER","solAmount":1}""",
            3_000L
        )!!
        val duplicate = event.copy(timestampEpochMs = 4_000L)
        assertEquals("SAME", event.dedupeKey())
        assertEquals(event.dedupeKey(), duplicate.dedupeKey())
    }

    @Test
    fun parsesSubscriptionConfirmation() {
        val ack = PumpDevParser.parseSubscriptionAck(
            """{"type":"subscribed","method":"subscribeTokenTrade","keys":["MINT"]}"""
        )
        assertNotNull(ack)
        assertEquals(listOf("MINT"), ack!!.keys)
    }

    @Test
    fun parsesSubscriptionLimitErrorWithoutMethodField() {
        val control = PumpDevParser.parseControlMessage(
            """{"type":"error","code":"SUBSCRIPTION_LIMIT","message":"limit reached","accepted":0,"dropped":1}"""
        )
        assertNotNull(control)
        assertEquals("error", control!!.type)
        assertEquals("SUBSCRIPTION_LIMIT", control.code)
        assertEquals("limit reached", control.message)
        assertEquals(true, control.isError)
    }

    @Test
    fun parsesAuthStatusWithoutKeyMaterial() {
        val control = PumpDevParser.parseControlMessage(
            """{"type":"auth","status":"ok","tier":"free"}"""
        )
        assertNotNull(control)
        assertEquals("ok", control!!.status)
        assertEquals("free", control.tier)
    }
}
