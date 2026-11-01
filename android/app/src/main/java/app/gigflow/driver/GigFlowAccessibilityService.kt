package app.gigflow.driver

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.view.Display
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent

/**
 * Watches the configured driver apps' windows, extracts offer cards, scores
 * them against the user's rules and shows a read-only floating verdict.
 * No interactions with driver apps are performed.
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
        // Voice is optional; no TTS installation should be required to scan.
        tts = if (settings.voiceAlerts) {
            try { TextToSpeech(this) { /* ready */ } }
            catch (e: Exception) { ServiceHealth.error(this, e); null }
        } else null
        instance = this
        ServiceHealth.connected(this)
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
            val scored = RuleEngine.scoreIfEligible(o, settings.rules()) ?: return@post
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
     * Uber may display its request in a separate floating window while another
     * app is foreground. The active root can then be our own Offers screen,
     * NOT the Uber request. Look at event.source AND all interactive windows.
     * Do not rely on the first matching app window having the offer card.
     */
    private fun offerRoots(event: AccessibilityEvent, pkg: String):
        List<android.view.accessibility.AccessibilityNodeInfo> {
        val roots = ArrayList<android.view.accessibility.AccessibilityNodeInfo>(6)
        fun include(node: android.view.accessibility.AccessibilityNodeInfo?) {
            if (node != null && node.packageName?.toString() == pkg) roots.add(node)
        }
        try {
            var root = event.source
            var hops = 0
            while (root != null && hops++ < 48) {
                val parent = root.parent ?: break
                root = parent
            }
            include(root)
        } catch (_: Exception) { }
        try {
            for (w in windows) include(w.root)
        } catch (_: Exception) { }
        try { include(rootInActiveWindow) } catch (_: Exception) { }
        return roots
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
        // This callback runs independently of the Moonbeam Activity.
        // A stale Uber/Lyft window must never bring the listener down.
        if (!::settings.isInitialized || !::overlay.isInitialized) return
        try {
            val pkg = event.packageName?.toString() ?: return
            ServiceHealth.event(this, pkg)
            processOfferEvent(event, pkg)
        } catch (e: Exception) {
            android.util.Log.e("MoonbeamSvc", "Offer scan failed; listening continues", e)
            try { ServiceHealth.error(this, e) } catch (_: Exception) { }
            try { offerGone() } catch (_: Exception) { }
        }
    }

    private fun processOfferEvent(event: AccessibilityEvent, pkg: String) {
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

        // OCR is the primary reader for the rendered offer. Do this before
        // consulting the view hierarchy: Uber/Lyft routinely provide an
        // event with no readable root, and that must still be an OCR attempt.
        recognizeScreenshot(pkg, prefs)

        val roots = offerRoots(event, pkg)
        if (roots.isEmpty()) {
            Diagnostics.record(this, pkg, "ocr_requested",
                listOf("(OCR requested; accessibility window not readable)"))
            maybeOfferGone(now)
            return
        }

        // Prefer an actually readable offer over whichever window Android
        // happened to call "active". Preserve the most informative snapshot
        // when all candidate windows fail to parse.
        val readings = roots.mapNotNull { root ->
            // Accessibility roots are ephemeral during app/window switches.
            try {
                val items = OfferParser.flatten(root)
                items to OfferParser.parse(pkg, items, extraRe, curSymbol)
            } catch (e: Exception) {
                android.util.Log.w("MoonbeamSvc", "Window changed during scan", e)
                null
            }
        }
        if (readings.isEmpty()) {
            maybeOfferGone(now)
            return
        }
        val best = readings.firstOrNull {
            it.second?.distanceKm != null && it.second?.durationMin != null
        } ?: readings.firstOrNull { it.second != null }
        val nodes = best?.first ?: readings.maxByOrNull { it.first.size }!!.first
        val offer = best?.second
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
        if (!RuleEngine.isEligible(offer)) {
            ignoreReservation(offer, pkg, nodes.map { it.diag() })
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

        val scored = RuleEngine.scoreIfEligible(offer, prefs) ?: return
        activeOffer = scored

        OfferLog.addIfNew(this, OfferLog.Entry(
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
        ), offer.fingerprint)

        // Mystro-style voice alert for the verdict, when enabled.
        if (settings.voiceAlerts) {
            if (tts == null) {
                try { tts = TextToSpeech(this) { /* ready */ } }
                catch (e: Exception) { ServiceHealth.error(this, e) }
            }
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

    private fun recognizeScreenshot(pkg: String, prefs: RulePrefs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val source = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                    val bitmap = source?.copy(Bitmap.Config.ARGB_8888, false)
                    source?.recycle()
                    screenshot.hardwareBuffer.close()
                    if (bitmap == null) return
                    OcrOfferRecognizer.recognize(bitmap, pkg, settings.currencyCode, settings.currencySymbol) { offer, texts ->
                        bitmap.recycle()
                        if (offer == null || offer.fingerprint == lastFingerprint) return@recognize
                        lastFingerprint = offer.fingerprint
                        lastSeenAt = System.currentTimeMillis()
                        val scored = RuleEngine.scoreIfEligible(offer, prefs)
                        if (scored == null) {
                            ignoreReservation(offer, pkg, texts)
                            return@recognize
                        }
                        activeOffer = scored
                        OfferLog.addIfNew(this@GigFlowAccessibilityService, OfferLog.Entry(
                            at = offer.seenAt, pkg = pkg, payoutCents = offer.payoutCents,
                            distanceKm = offer.distanceKm, durationMin = offer.durationMin,
                            perMileCents = scored.perMileCents, perHourCents = scored.perHourCents,
                            verdict = scored.verdict.name, action = "shown", currency = offer.currencySymbol,
                        ), offer.fingerprint)
                        Diagnostics.record(this@GigFlowAccessibilityService, pkg, "offer", texts)
                        handler.post { overlay.show(scored) { overlay.hide() } }
                    }
                }
                override fun onFailure(errorCode: Int) {
                    Diagnostics.record(this@GigFlowAccessibilityService, pkg, "ocr_unavailable",
                        listOf("Screenshot capture unavailable (Android error $errorCode); using accessibility text."))
                }
            })
        } catch (_: SecurityException) {
            Diagnostics.record(this, pkg, "ocr_unavailable",
                listOf("Screenshot capture blocked by this Android build; using accessibility text."))
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

    /** Clear any prior live verdict and remember this tile without scoring,
     * logging or speaking it. This also deduplicates OCR/accessibility passes. */
    private fun ignoreReservation(offer: DetectedOffer, pkg: String, texts: List<String>) {
        hideRunnable?.let { handler.removeCallbacks(it) }
        hideToken = ""
        activeOffer = null
        overlay.hide()
        lastFingerprint = offer.fingerprint
        lastSeenAt = System.currentTimeMillis()
        Diagnostics.record(this, pkg, "reservation_ignored", texts)
    }

    private fun offerGone() {
        hideRunnable?.let { handler.removeCallbacks(it) }
        hideToken = ""
        activeOffer = null
        lastFingerprint = ""
        overlay.hide()
    }

    override fun onInterrupt() {
        // Feedback interruption is not service shutdown: keep listening.
        if (::overlay.isInitialized) {
            try { offerGone() } catch (e: Exception) { ServiceHealth.error(this, e) }
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        ServiceHealth.disconnected(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        ServiceHealth.disconnected(this)
        hideRunnable?.let { handler.removeCallbacks(it) }
        if (::overlay.isInitialized) {
            try { overlay.hide() } catch (_: Exception) { }
        }
        try { tts?.shutdown() } catch (_: Exception) { }
        tts = null
        super.onDestroy()
    }
}
