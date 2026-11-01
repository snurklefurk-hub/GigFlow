package app.gigflow.driver

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import app.gigflow.driver.ui.*

/**
 * Moonbeam by NightRider — read-only offer companion.
 *
 * Two focused screens hosted in a single activity (no fragments, no deps):
 *   Offers · Reader
 *
 * The accessibility service runs independently of this UI — see
 * GigFlowAccessibilityService.
 */
class MainActivity : Activity() {

    private lateinit var settings: SettingsRepository
    private lateinit var db: LocalDb
    private lateinit var p: Ios.Palette

    private lateinit var offers: OffersScreen
    private lateinit var assist: AssistScreen

    private lateinit var content: FrameLayout
    private lateinit var tabBar: TabBar
    private var current = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsRepository(this)
        db = LocalDb(this)
        p = Ios.palette(this)

        offers = OffersScreen(this, p, settings)
        assist = AssistScreen(this, p, settings, db)

        content = FrameLayout(this)
        tabBar = TabBar(this, p)
        tabBar.setTabs(listOf(
            TabBar.Tab("tag", "Offers"),
            TabBar.Tab("slider", "Reader"),
        ))
        tabBar.onSelect = { show(it) }

        val root = FrameLayout(this).apply {
            addView(content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(tabBar, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setBackgroundColor(p.groupedBg)
        }
        setContentView(root)

        // Use normal Android navigation. Never hide the system navigation bar:
        // the driver's Back/Home/Recents buttons (or gesture handle) stay visible.
        // Android 15+ uses edge-to-edge; respect system bar insets so the app's
        // own bottom tabs aren't covered by the phone's navigation controls.
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { ctl ->
                ctl.show(WindowInsets.Type.systemBars())
                val lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                ctl.setSystemBarsAppearance(if (p.isDark) 0 else lightBars, lightBars)
            }
            root.setOnApplyWindowInsetsListener { _, insets ->
                val sys = insets.getInsets(WindowInsets.Type.systemBars())
                content.setPadding(0, sys.top, 0, 0)
                tabBar.setPadding(0, 0, 0, sys.bottom)
                insets
            }
            root.requestApplyInsets()
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (p.isDark) 0 else (
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            )
        }

        show(0)
        handleScreenshotIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleScreenshotIntent(intent)
    }

    @Deprecated("Activity result API is sufficient for this small, no-dependency picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4101 && resultCode == RESULT_OK) data?.data?.let(::importScreenshot)
    }

    /** Share-sheet and picker entry point. The OCR result is always reviewed. */
    fun importScreenshot(uri: Uri) {
        val bytes = try { contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (_: Exception) { null }
        val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (bitmap == null || bytes == null) { Toast.makeText(this, "Couldn't open that image", Toast.LENGTH_SHORT).show(); return }
        OcrOfferRecognizer.recognize(bitmap, "com.ubercab.driver", settings.currencyCode, settings.currencySymbol) { offer, _ ->
            runOnUiThread { showCorrection(offer, bytes) }
        }
    }

    private fun handleScreenshotIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || !intent.type.orEmpty().startsWith("image/")) return
        @Suppress("DEPRECATION") val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
        importScreenshot(uri)
    }

    private fun showCorrection(parsed: DetectedOffer?, imageBytes: ByteArray) {
        val offer = parsed ?: DetectedOffer("com.ubercab.driver", 0, null, null, null, null,
            acceptNode = null, declineNode = null, rawTexts = emptyList(), currencySymbol = settings.currencySymbol)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0) }
        fun field(label: String, value: String) = EditText(this).apply {
            hint = label; setText(value); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }.also(col::addView)
        val payout = field("Payout", "%.2f".format(offer.payoutCents / 100.0))
        val distance = field("Total miles", offer.distanceKm?.let { "%.2f".format(it / 1.609344) } ?: "")
        val minutes = field("Total minutes", offer.durationMin?.toString() ?: "")
        AlertDialog.Builder(this).setTitle("Review screenshot OCR")
            .setMessage("Correct fields before saving a private regression case. The image is not retained.")
            .setView(col).setNegativeButton("Discard", null).setPositiveButton("Save locally") { _, _ ->
                val fixed = offer.copy(payoutCents = ((payout.text.toString().toDoubleOrNull() ?: 0.0) * 100).toInt(),
                    distanceKm = distance.text.toString().toDoubleOrNull()?.times(1.609344), durationMin = minutes.text.toString().toDoubleOrNull())
                LocalRegressionCorpus.add(this, imageBytes, fixed)
                Toast.makeText(this, "Saved locally (${LocalRegressionCorpus.size(this)} cases)", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun show(i: Int) {
        current = i
        val target = when (i) {
            0 -> offers.screen
            else -> assist.screen
        }
        if (target.parent == content) return
        content.removeAllViews()
        target.alpha = 0f
        content.addView(target, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        target.animate().alpha(1f).setDuration(Ios.ANIM_FAST).start()
        refresh(i)
    }

    private fun refresh(i: Int): Unit {
        Ios.hapticsEnabled = settings.haptics
        return when (i) {
            0 -> offers.refresh()
            else -> assist.refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::tabBar.isInitialized) refresh(current)
    }

}
