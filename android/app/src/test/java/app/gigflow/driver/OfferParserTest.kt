package app.gigflow.driver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real-world offer layouts reproduced from field screenshots; no UI mocks needed. */
class OfferParserTest {

    @Test fun uberExclusiveWithPickupAndTripLegs() {
        val offer = OfferParser.parseTexts("com.uber.driver", listOf(
            "UberX", "Exclusive", "$19.06",
            "20 min (13.7 mi)",
            "Coconut Dr & Stone Crab Cir, Bloomingdale",
            "26 mins (19.4 mi)",
            "Barnard St & W Congress Ln, Savannah",
            "Accept",
        ))
        assertNotNull(offer)
        assertEquals(1906, offer!!.payoutCents)
        assertEquals(33.1, offer.distanceKm!! / 1.609344, 0.11)
        assertEquals(46.0, offer.durationMin!!, 0.01)
        val scored = RuleEngine.score(offer, RulePrefs())
        assertTrue(scored.perMileCents in 55..60)
        assertTrue(scored.perHourCents in 2480..2490)
        assertTrue(scored.verdict != Verdict.GOOD)
    }

    @Test fun singleRideMetricsStillParse() {
        val offer = OfferParser.parseTexts("com.lyft.driver", listOf(
            "Lyft", "$12.50", "7.5 mi", "22 min", "Accept",
        ))
        assertNotNull(offer)
        assertEquals(1250, offer!!.payoutCents)
        assertEquals(7.5, offer.distanceKm!! / 1.609344, 0.11)
        assertEquals(22.0, offer.durationMin!!, 0.01)
    }

    @Test fun threeDifferentLegsStayUnreadableRatherThanGuessing() {
        val offer = OfferParser.parseTexts("com.uber.driver", listOf(
            "UberX", "$19.06", "2 mi", "3 mi", "4 mi", "10 min", "15 min", "20 min",
        ))
        assertNull(offer)
    }

    @Test fun multipleConflictingPayoutsAreNeverGuessed() {
        val offer = OfferParser.parseTexts("com.uber.driver", listOf(
            "$19.06", "$25.00", "20 min (13.7 mi)", "26 min (19.4 mi)",
        ))
        assertNull(offer)
    }
}
