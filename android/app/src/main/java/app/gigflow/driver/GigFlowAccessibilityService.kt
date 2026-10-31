package app.gigflow.driver

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent

/**
 * Watches the configured driver apps' windows, extracts offer cards, scores
 * them against the user's rules, shows the floating verdict, and optionally
 * taps accept/decline when automation toggles are on.
 *
 * Everything runs on-device — no screen content or offer data leaves the phone.
 */
class GigFlowAccessibilityService : AccessibilityService() {

    private lateinit var settings: SettingsRepository
    private lateinit var overlay: OverlayController

    private var lastFingerprint = ""
    private var lastSeenAt = 0L
    private var lastScanAt = 0L
    private var activeOffer: ScoredOffer? = null

    private val handler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null
    private var hideToken = ""
    private var tts: TextToSpeech? = null
    private var localDb: LocalDb? = null

    companion object {
        /** Frames can flicker mid-animation; give an offer this long to
         *  reappear before hiding the overlay. */
        private const val MISS_GRACE_MS = 2_000L

        /** Live instance while the service is enabled — null otherwise. */
        @Volatile var instance: GigFlowAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = SettingsRepository(this)
        overlay = OverlayController(this)
        localDb = LocalDb(this)
        tts = TextToSpeech(this) { /* ready */ }
        instance = this
    }

    /**
     * Synthetic test offer: exercise scoring, overlay and voice without
     * interacting with either driver app or logging a fake ride.
     */
    fun testOffer() {
        handler.post {
            val o = DetectedOffer(
                appPackage = "app.gigflow.test",
                payoutCents = (12.50 * 100).toInt(),
                tipCents = 200,
                distanceKm = 4.2,
                durationMin = 18.0,
                extraDistanceKm = null,
                acceptNode = null,
                declineNode = null,
                rawTexts = listOf("Test offer"),
                currencySymbol = settings.currencySymbol,
                expiresInSec = 12,
            )
            val scored = RuleEngine.score(o, settings.rules())
            activeOffer = scored // so stopWatching()/onInterrupt clear it too
            overlay.show(scored) { action ->
                if (action == OverlayController.Action.EXPIRED) offerGone()
                overlay.hide()
            }
            if (settings.voiceAlerts) {
                tts?.speak(
                    "Test offer, ${spokenAmount(o.payoutCents, o.currencySymbol)}",
                    TextToSpeech.QUEUE_FLUSH, null, "test",
                )
            }
        }
    }

    /**
     * UI-initiated teardown. Our own package's events never reach
     * onAccessibilityEvent (manifest filter), so turning monitoring off
     * inside the app wouldn't clear the overlay/pending taps — the
     * Monitoring toggle calls this directly.
     */
    fun stopWatching() = handler.post { offerGone() }

    /**
     * Offer cards often live in a popup/dialog window that isn't the
     * "active" window, and rootInActiveWindow is a known-null API. Fall
     * back to scanning all interactive windows for the driver app.
     */
    private fun appWindowRoot(pkg: String): android.view.accessibility.AccessibilityNodeInfo? {
        rootInActiveWindow?.let { if (it.packageName?.toString() == pkg) return it }
        try {
            for (w in windows) {
                val r = w.root ?: continue
                if (r.packageName?.toString() == pkg) return r
            }
        } catch (_: Exception) { }
        return null
    }

    /** Hide the overlay only after the offer's been missing for a bit —
     *  single-frame parse misses are normal (animations, partial draws)
     *  and shouldn't flicker the verdict off. */
    private fun maybeOfferGone(now: Long) {
        if (activeOffer == null) return
        if (now - lastSeenAt < MISS_GRACE_MS) return
        offerGone()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        android.util.Log.d("GigFlowSvc", "event pkg=${event.packageName} type=${event.eventType}")
        val pkg = event.packageName?.toString() ?: return
        val now = System.currentTimeMillis()

        val prefs = settings.rulesFor(pkg) // per-app overrides over globals
        // Monitoring switched off while an overlay was up — tear it down.
        if (!prefs.enabled) { offerGone(); return }
        if (now - lastScanAt < 350) return // throttle contentChanged storms
        lastScanAt = now

        // Symbols beyond the built-in set (R$, ₩, KSh…) match through the
        // user's configured currency so any market parses.
        val curSymbol = settings.currencySymbol
        val extraRe = OfferParser.extraMoney(settings.currencyCode, curSymbol)

        val root = appWindowRoot(pkg) ?: run {
            // Window exists but its content isn't retrievable (or the window
            // list hasn't caught up yet) — record it so Diagnostics shows
            // "we saw the app but couldn't read it" rather than nothing.
            Diagnostics.record(this, pkg, "idle", listOf("(window not readable)"))
            maybeOfferGone(now)
            return
        }

        val nodes = OfferParser.flatten(root)
        val offer = OfferParser.parse(pkg, nodes, extraRe, curSymbol)
        if (offer == null) {
            // A screen with a money figure but no parse is a "miss" — the
            // card was probably there but the layout beat the heuristic.
            // Record it so Diagnostics can show what we saw.
            val hasMoney = nodes.any {
                OfferParser.moneyOf(it.text, extraRe, curSymbol) != null
            }
            Diagnostics.record(this, pkg, if (hasMoney) "miss" else "idle",
                nodes.map { it.diag() })
            maybeOfferGone(now)
            return
        }
        Diagnostics.record(this, pkg, "offer", nodes.map { it.diag() })

        // Same card still on screen — don't rescore/re-overlay every frame.
        if (offer.fingerprint == lastFingerprint && now - lastSeenAt < 60_000) {
            lastSeenAt = now
            return
        }
        lastFingerprint = offer.fingerprint
        lastSeenAt = now

        val scored = RuleEngine.score(offer, prefs)
        activeOffer = scored

        OfferLog.add(this, OfferLog.Entry(
            at = offer.seenAt,
            pkg = pkg,
            payoutCents = offer.payoutCents,
            distanceKm = offer.distanceKm,
            durationMin = offer.durationMin,
            perMileCents = scored.perMileCents,
            perHourCents = scored.perHourCents,
            verdict = scored.verdict.name,
            action = "shown",
            lat = null,
            lng = null,
            reservation = offer.isReservation,
            currency = offer.currencySymbol,
        ))

        // Mystro-style voice alert for the verdict, when enabled.
        if (settings.voiceAlerts) {
            val label = if (offer.distanceKm == null || offer.durationMin == null) {
                "Incomplete offer"
            } else when (scored.verdict) {
                Verdict.GOOD -> "Good offer"
                Verdict.MEH -> "Borderline offer"
                Verdict.BAD -> "Skip it"
            }
            tts?.speak(
                "$label, ${spokenAmount(offer.payoutCents, offer.currencySymbol)}",
                TextToSpeech.QUEUE_FLUSH, null, "offer",
            )
        }

        overlay.show(scored) { action ->
            if (action == OverlayController.Action.EXPIRED) offerGone()
            overlay.hide()
        }

        // Optional overlay timeout — 0 = stay until the card disappears.
        val secs = prefs.overlaySeconds
        if (secs > 0) {
            val token = offer.fingerprint
            hideToken = token
            hideRunnable?.let { handler.removeCallbacks(it) }
            val r = Runnable { if (hideToken == token) overlay.hide() }
            hideRunnable = r
            handler.postDelayed(r, secs * 1000L)
        }

    }


    /** "12.50 dollars" / "1850 forints" — names and zero-decimal handling
     *  come from the currency registry. */
    private fun spokenAmount(cents: Int, symbol: String): String =
        Currencies.spokenAmount(cents, symbol)


    /** "· [tap] (btn_accept) Elfogadás" — clickability and the view id are
     *  what a parse-miss report needs: whether the app exposes real buttons
     *  vs a tap-anywhere card, and which control we should have hit. */
    private fun OfferParser.FlatNode.diag() = buildString {
        if (clickable) append("[tap] ")
        viewId?.substringAfterLast('/')?.let { append("($it) ") }
        append(text)
    }

    private fun offerGone() {
        hideRunnable?.let { handler.removeCallbacks(it) }
        hideToken = ""
        activeOffer = null
        lastFingerprint = ""
        overlay.hide()
    }

    override fun onInterrupt() {
        offerGone()
    }

    override fun onDestroy() {
        instance = null
        overlay.hide()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
