package com.solanasignal.app.data.pumpportal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PumpDevEventParserTest {
    @Test fun parsesNativeSolTradeAndSourceTimestamp() {
        val parsed = PumpDevEventParser.parse(
            """{"signature":"sig","mint":"mint","traderPublicKey":"wallet","txType":"buy","quoteMint":"So11111111111111111111111111111111111111112","quoteAmount":0.5,"solAmount":0.5,"tokenAmount":1000000,"marketCapSol":40.5,"timestamp":1743782400}""",
            1_743_782_401_000L
        ) as ParseResult.Trade
        assertEquals("PUMPDEV", parsed.event.providerSource)
        assertEquals(TradeSide.BUY, parsed.event.side)
        assertEquals(0.5, parsed.event.solAmount!!, 0.0)
        assertEquals(40.5, parsed.event.marketCapSol!!, 0.0)
        assertEquals(1_743_782_400_000L, parsed.event.sourceTimestampEpochMs)
        assertEquals("sig", parsed.event.signature)
    }

    @Test fun leavesUnresolvedNonSolQuoteUnknown() {
        val parsed = PumpDevEventParser.parse(
            """{"mint":"mint","txType":"buy","quoteMint":"EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v","quoteTokenDecimals":null,"quoteAmount":null,"solAmount":null,"marketCapQuote":null,"quoteContextResolved":false,"source":"pumpswap"}""",
            10_000L
        ) as ParseResult.Trade
        assertNull(parsed.event.solAmount)
        assertNull(parsed.event.marketCapSol)
        assertNull(parsed.event.priceSol)
    }

    @Test fun pricesSolPumpSwapFromEffectivePoolReservesOnly() {
        val parsed = PumpDevEventParser.parse(
            """{"mint":"mint","txType":"buy","quoteMint":"So11111111111111111111111111111111111111112","quoteAmount":0.5,"source":"pumpswap","poolEffectiveQuoteReservesUi":85.0,"poolBaseReservesUi":800000.0}""",
            10_000L
        ) as ParseResult.Trade
        assertEquals(85.0 / 800_000.0, parsed.event.priceSol!!, 1e-12)
    }

    @Test fun recognizesProviderControlFramesAndBothMigrationEvents() {
        val ack = PumpDevEventParser.parse("""{"type":"subscribed","method":"subscribeTokenTrade","keys":["mint"]}""", 10_000L)
        assertTrue(ack is ParseResult.Control)
        assertEquals("subscribed", (ack as ParseResult.Control).controlType)
        assertTrue(PumpDevEventParser.parse("""{"mint":"mint","txType":"complete","timestamp":1743782400}""", 10_000L) is ParseResult.Migration)
        assertTrue(PumpDevEventParser.parse("""{"mint":"mint","txType":"create_pool","timestamp":1743782400}""", 10_000L) is ParseResult.Migration)
    }

    @Test fun refusesMarketEventsWithoutMintInsteadOfCreatingSyntheticIdentity() {
        val parsed = PumpDevEventParser.parse("""{"txType":"buy","quoteMint":"So11111111111111111111111111111111111111112"}""", 10_000L)
        assertTrue(parsed is ParseResult.Malformed)
    }
}
