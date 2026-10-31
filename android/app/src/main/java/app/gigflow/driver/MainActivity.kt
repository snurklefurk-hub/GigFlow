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

        // Edge-to-edge: content flows under the status bar; the nav bar hides
        // and only reappears on a bottom swipe (transient, never resizes us).
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { ctl ->
                ctl.hide(WindowInsets.Type.navigationBars())
                ctl.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            root.setOnApplyWindowInsetsListener { _, insets ->
                val sys = insets.getInsets(WindowInsets.Type.systemBars())
                content.setPadding(0, sys.top, 0, 0)
                tabBar.setPadding(0, 0, 0, sys.bottom)
                insets
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
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
