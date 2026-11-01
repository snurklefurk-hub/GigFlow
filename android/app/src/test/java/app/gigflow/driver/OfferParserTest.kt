package app.gigflow.driver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real-world offer layouts reproduced from field screenshots; no UI mocks needed. */
class OfferParserTest {

    @Test fun uberExclusiveWithPickupAndTripLegs() {
        val offer = OfferParser.parseTexts("com.ubercab.driver", listOf(
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

    // Reproduced from the driver's Oct 31 field screenshots. These are
    // screen labels, not a proof of which AccessibilityNodeInfos Uber/Lyft
    // expose on a particular Android device.
    @Test fun uberLiveOfferDoesNotAddIncludedBoostToFare() {
        val offer = OfferParser.parseTexts("com.ubercab.driver", listOf(
            "UberX", "Exclusive", "$16.01",
            "5.00", "Verified", "+$1.75 included",
            "10 min (6.3 mi)", "Abercorn St & E York St",
            "20 mins (5.2 mi)", "Billy Mitchell Blvd", "Accept",
        ))
        assertNotNull(offer)
        assertEquals(1601, offer!!.payoutCents)
        assertNull(offer.tipCents)
        assertEquals(11.5, offer.distanceKm!! / 1.609344, 0.02)
        assertEquals(30.0, offer.durationMin!!, 0.01)
        val scored = RuleEngine.score(offer, RulePrefs())
        assertTrue(scored.perMileCents in 138..141)
        assertEquals(3202, scored.perHourCents)
    }

    @Test fun lyftLiveOfferIgnoresEstimatedHourlyRateAsFare() {
        val offer = OfferParser.parseTexts("com.lyft.android.driver", listOf(
            "$10.00", "$26.08/hr est. rate for this ride",
            "7 min · 4.5 mi", "Fenwick Village, Savannah",
            "16 min · 10.3 mi", "Augusta & Minis, Savannah",
            "Drop-off in 20% Turbo Zone", "5.0", "Lyft", "Accept",
        ))
        assertNotNull(offer)
        assertEquals(1000, offer!!.payoutCents)
        assertEquals(14.8, offer.distanceKm!! / 1.609344, 0.02)
        assertEquals(23.0, offer.durationMin!!, 0.01)
        val scored = RuleEngine.score(offer, RulePrefs())
        assertTrue(scored.perMileCents in 67..69)
        assertEquals(2609, scored.perHourCents)
    }

    @Test fun joinedFareAndBonusNodeDoesNotBecomeDoublePayout() {
        val offer = OfferParser.parseTexts("com.ubercab.driver", listOf(
            "UberX Exclusive $16.01 +$1.75 included",
            "10 min (6.3 mi)", "20 mins (5.2 mi)", "Accept",
        ))
        assertNotNull(offer)
        assertEquals(1601, offer!!.payoutCents)
        assertEquals(11.5, offer.distanceKm!! / 1.609344, 0.02)
    }

    @Test fun hourlyRateSplitIntoSeparateNodeIsNotASecondOffer() {
        val offer = OfferParser.parseTexts("com.lyft.android.driver", listOf(
            "$10.00", "$26.08", "/hr est. rate for this ride",
            "7 min · 4.5 mi", "16 min · 10.3 mi", "Accept",
        ))
        assertNotNull(offer)
        assertEquals(1000, offer!!.payoutCents)
    }

    @Test fun identicalPickupAndTripLegsAreBothIncluded() {
        val offer = OfferParser.parseTexts("com.ubercab.driver", listOf(
            "$10.00", "7 min (4.5 mi)", "7 min (4.5 mi)", "Accept",
        ))
        assertNotNull(offer)
        assertEquals(9.0, offer!!.distanceKm!! / 1.609344, 0.02)
        assertEquals(14.0, offer.durationMin!!, 0.01)
    }

    @Test fun secondaryHourlyRateAloneIsNotMistakenForPayout() {
        assertNull(OfferParser.parseTexts("com.lyft.android.driver", listOf(
            "$26.08/hr est. rate for this ride",
            "7 min · 4.5 mi", "16 min · 10.3 mi", "Accept",
        )))
    }

    @Test fun singleRideMetricsStillParse() {
        val offer = OfferParser.parseTexts("com.lyft.android.driver", listOf(
            "Lyft", "$12.50", "7.5 mi", "22 min", "Accept",
        ))
        assertNotNull(offer)
        assertEquals(1250, offer!!.payoutCents)
        assertEquals(7.5, offer.distanceKm!! / 1.609344, 0.11)
        assertEquals(22.0, offer.durationMin!!, 0.01)
    }

    @Test fun threeDifferentLegsStayUnreadableRatherThanGuessing() {
        val offer = OfferParser.parseTexts("com.ubercab.driver", listOf(
            "UberX", "$19.06", "2 mi", "3 mi", "4 mi", "10 min", "15 min", "20 min",
        ))
        assertNull(offer)
    }

    @Test fun multipleConflictingPayoutsAreNeverGuessed() {
        val offer = OfferParser.parseTexts("com.ubercab.driver", listOf(
            "$19.06", "$25.00", "20 min (13.7 mi)", "26 min (19.4 mi)",
        ))
        assertNull(offer)
    }
    @Test fun uberExclusiveLongTripWithIncludedBoost() {
        val offer = OfferParser.parseTexts("com.ubercab.driver", listOf(
            "UberX", "Exclusive", "$57.07", "4.82", "Verified", "+$5.25 included",
            "20 min (13.2 mi)", "W State St & Whitaker St, Savannah",
            "51 mins (37.3 mi)", "William Hilton Pkwy, Hilton Head Island",
            "Long trip (45+ min)", "Accept",
        ))
        assertNotNull(offer)
        assertEquals(5707, offer!!.payoutCents)
        assertEquals(50.5, offer.distanceKm!! / 1.609344, 0.02)
        assertEquals(71.0, offer.durationMin!!, 0.01)
    }

    @Test fun lyftOfferWithEstimatedHourlyRateAndTurboZone() {
        val offer = OfferParser.parseTexts("com.lyft.android.driver", listOf(
            "$10.15", "$24.36/hr est. rate for this ride",
            "4 min · 1.5 mi", "Kelsey & Kenwood, Bluffton",
            "21 min · 11.2 mi", "Bluffton, Bluffton",
            "Drop-off in 10% Turbo Zone", "Mike", "5.0", "Accept",
        ))
        assertNotNull(offer)
        assertEquals(1015, offer!!.payoutCents)
        assertEquals(12.7, offer.distanceKm!! / 1.609344, 0.06)
        assertEquals(25.0, offer.durationMin!!, 0.01)
    }

    @Test fun uberMatchOfferHasSameTwoLegScoring() {
        val offer = OfferParser.parseTexts("com.ubercab.driver", listOf(
            "UberX", "$8.08", "5.00", "Verified",
            "5 min (2.7 mi)", "New Riverside Rd & Wheelhouse Way, Bluffton",
            "20 mins (11.5 mi)", "Fording Island Rd, Bluffton", "Match",
        ))
        assertNotNull(offer)
        assertEquals(808, offer!!.payoutCents)
        assertEquals(14.2, offer.distanceKm!! / 1.609344, 0.06)
        assertEquals(25.0, offer.durationMin!!, 0.01)
    }

    @Test fun uberReservationTileIsRecognizedButNeverScored() {
        val offer = OfferParser.parseTexts("com.ubercab.driver", listOf(
            "UberX", "Reservation", "$28.42",
            "12 min (7.1 mi)", "Pickup at 6:45 PM",
            "24 min (15.3 mi)", "Accept",
        ))
        assertNotNull(offer)
        assertTrue(offer!!.isReservation)
        assertTrue(!RuleEngine.isEligible(offer))
        assertNull(RuleEngine.scoreIfEligible(offer, RulePrefs()))
    }

}
