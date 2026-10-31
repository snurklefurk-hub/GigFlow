package app.gigflow.driver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Safety regression checks: a partial offer must not be labeled profitable.
 * These tests intentionally do not require an Android emulator.
 */
class RuleEngineTest {
    private fun offer(
        payoutCents: Int = 1500,
        distanceKm: Double? = 5.0,
        durationMin: Double? = 15.0,
    ) = DetectedOffer(
        appPackage = "com.lyft.driver",
        payoutCents = payoutCents,
        tipCents = null,
        distanceKm = distanceKm,
        durationMin = durationMin,
        extraDistanceKm = null,
        acceptNode = null,
        declineNode = null,
        rawTexts = listOf("$15.00", "3 mi", "15 min"),
        currencySymbol = "$",
        expiresInSec = null,
    )

    @Test fun completeProfitableOfferCanBeGood() {
        val scored = RuleEngine.score(offer(), RulePrefs())
        assertEquals(Verdict.GOOD, scored.verdict)
        assertTrue(scored.perMileCents != null)
        assertTrue(scored.perHourCents != null)
    }

    @Test fun missingDistanceCannotBeGood() {
        val scored = RuleEngine.score(offer(distanceKm = null), RulePrefs())
        assertEquals(Verdict.MEH, scored.verdict)
        assertTrue(scored.reasons.contains("distance unreadable"))
    }

    @Test fun missingDurationCannotBeGood() {
        val scored = RuleEngine.score(offer(durationMin = null), RulePrefs())
        assertEquals(Verdict.MEH, scored.verdict)
        assertTrue(scored.reasons.contains("duration unreadable"))
    }

    @Test fun missingBothCannotBeGood() {
        val scored = RuleEngine.score(offer(distanceKm = null, durationMin = null), RulePrefs())
        assertEquals(Verdict.MEH, scored.verdict)
    }

    @Test fun lowProfitCompleteOfferIsBad() {
        val scored = RuleEngine.score(
            offer(payoutCents = 100, distanceKm = 50.0, durationMin = 60.0),
            RulePrefs(),
        )
        assertEquals(Verdict.BAD, scored.verdict)
    }
}
