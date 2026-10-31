package app.gigflow.driver.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.Interpolator

/**
 * iOS-inspired design tokens for the companion app. Mirrors Apple's system
 * palette (light + dark), type scale, radii, and motion curves — hand-rolled
 * on plain Android views, no dependencies.
 */
object Ios {

    // --- Motion -------------------------------------------------------------
    // Critically-damped-ish spring: slight overshoot then settle, like UIKit's
    // default spring (dampingRatio ~0.8).
    val SPRING: Interpolator = Interpolator { t ->
        val s = 1.70158f * 1.3f
        val x = t - 1f
        x * x * ((s + 1f) * x + s) + 1f
    }
    val EASE_OUT: Interpolator = android.view.animation.DecelerateInterpolator(1.8f)
    const val ANIM_MS = 320L
    const val ANIM_FAST = 180L

    /** Global toggle mirrored from SettingsRepository.haptics. */
    @Volatile var hapticsEnabled = true

    fun View.haptic() {
        if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun View.pressable(scale: Float = 0.965f) {
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(scale).scaleY(scale).alpha(0.85f)
                        .setDuration(ANIM_FAST).setInterpolator(EASE_OUT).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(ANIM_MS).setInterpolator(SPRING).start()
                }
            }
            false
        }
    }

    // --- Palette -------------------------------------------------------------
    class Palette(val isDark: Boolean) {
        val bg: Int
        val groupedBg: Int // background behind grouped lists
        val card: Int      // secondarySystemGroupedBackground
        val card2: Int     // tertiarySystemGroupedBackground / fill
        val fill: Int      // quaternary fill (segmented track)
        val label: Int
        val label2: Int
        val label3: Int
        val separator: Int
        val tint: Int
        val green: Int
        val red: Int
        val orange: Int
        val yellow: Int
        val gray: Int
        val sheetHandle: Int

        init {
            if (isDark) {
                bg = Color.parseColor("#0B1625")
                groupedBg = Color.parseColor("#0B1625")
                card = Color.parseColor("#18283A")
                card2 = Color.parseColor("#233B50")
                fill = Color.parseColor("#30485C")
                label = Color.parseColor("#FFFFFF")
                label2 = Color.parseColor("#BED2DA")
                label3 = Color.parseColor("#8EAFBC")
                separator = Color.parseColor("#385064")
                tint = Color.parseColor("#8EE6BE")
                green = Color.parseColor("#74D7A4")
                red = Color.parseColor("#FF453A")
                orange = Color.parseColor("#F4B167")
                yellow = Color.parseColor("#F8C979")
                gray = Color.parseColor("#78939E")
                sheetHandle = Color.parseColor("#688093")
            } else {
                bg = Color.parseColor("#F6FAFB")
                groupedBg = Color.parseColor("#EDF3F5")
                card = Color.parseColor("#FFFFFF")
                card2 = Color.parseColor("#E4EEF0")
                fill = Color.parseColor("#D5E5E7")
                label = Color.parseColor("#112A39")
                label2 = Color.parseColor("#486779")
                label3 = Color.parseColor("#678590")
                separator = Color.parseColor("#A6C3CC")
                tint = Color.parseColor("#166B61")
                green = Color.parseColor("#20875E")
                red = Color.parseColor("#FF3B30")
                orange = Color.parseColor("#A5662B")
                yellow = Color.parseColor("#AD812D")
                gray = Color.parseColor("#78939E")
                sheetHandle = Color.parseColor("#B6CCD0")
            }
        }
    }

    fun palette(ctx: Context): Palette {
        val night = (ctx.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return Palette(night)
    }

    // --- Metrics --------------------------------------------------------------
    fun dp(ctx: Context, v: Float) = (v * ctx.resources.displayMetrics.density).toInt()
    fun sp(ctx: Context, v: Float) = v * ctx.resources.displayMetrics.scaledDensity

    const val R_CARD = 13f   // iOS grouped card
    const val R_PILL = 20f
    const val R_BUTTON = 12f
    const val R_SHEET = 14f

    // Type scale (sp) — Apple's text styles
    const val T_LARGE = 34f
    const val T_TITLE2 = 22f
    const val T_HEADLINE = 17f
    const val T_BODY = 17f
    const val T_SUBHEAD = 15f
    const val T_FOOTNOTE = 13f
    const val T_CAPTION = 12f

    // --- Drawing helpers ------------------------------------------------------
    fun rounded(radiusDp: Float, color: Int, ctx: Context) = GradientDrawable().apply {
        cornerRadius = dp(ctx, radiusDp).toFloat()
        setColor(color)
    }

    fun stroke(radiusDp: Float, color: Int, strokeColor: Int, ctx: Context) = GradientDrawable().apply {
        cornerRadius = dp(ctx, radiusDp).toFloat()
        setColor(color)
        setStroke(dp(ctx, 0.5f).coerceAtLeast(1), strokeColor)
    }

    /** iOS-style shadowed card background. */
    fun cardBg(p: Palette, ctx: Context) = GradientDrawable().apply {
        cornerRadius = dp(ctx, R_CARD).toFloat()
        setColor(p.card)
    }

    /** Currency-aware money render — placement/decimals come from the
     *  registry in [Currencies] ("1 850 Ft", "CHF 12.50", "¥1,200"). */
    fun money(cents: Int?, symbol: String = "$"): String =
        cents?.let { app.gigflow.driver.Currencies.format(it, symbol) } ?: "—"

    fun verdictColor(v: VerdictCompat, p: Palette) = when (v) {
        VerdictCompat.GOOD -> p.green
        VerdictCompat.MEH -> p.orange
        VerdictCompat.BAD -> p.red
    }

    /** Loose duplicate of the service-side enum for UI use. */
    enum class VerdictCompat { GOOD, MEH, BAD }
}
