package app.gigflow.driver

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.ViewGroup
import android.widget.FrameLayout
import app.gigflow.driver.ui.*

/**
 * GigFlow Driver — iOS-styled companion.
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
