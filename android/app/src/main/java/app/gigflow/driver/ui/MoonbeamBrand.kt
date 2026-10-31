package app.gigflow.driver.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.gigflow.driver.R
import app.gigflow.driver.ui.Ios.dp

/** Moonbeam's compact brand mark: same identity on Offers and Reader screens. */
object MoonbeamBrand {
    fun header(ctx: Context): View {
        val outer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16f), dp(ctx, 10f), dp(ctx, 16f), dp(ctx, 10f))
        }
        val surface = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(12, 28, 48), Color.rgb(25, 51, 68)),
        ).apply {
            cornerRadius = dp(ctx, 17f).toFloat()
            setStroke(dp(ctx, 1f), Color.rgb(47, 84, 91))
        }
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = surface
            setPadding(dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 14f), dp(ctx, 12f))
        }
        panel.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.moonbeam_foreground)
            contentDescription = "Crescent moon cutting a beam through clouds"
            background = Ios.rounded(12f, Color.rgb(9, 22, 38), ctx)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(ctx, 66f), dp(ctx, 66f)).apply {
            rightMargin = dp(ctx, 12f)
        })
        panel.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(ctx).apply {
                text = "MOONBEAM"
                textSize = 21f
                letterSpacing = 0.12f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(Color.rgb(246, 240, 225))
            })
            addView(TextView(ctx).apply {
                text = "by NightRider"
                textSize = Ios.T_CAPTION
                letterSpacing = 0.09f
                setTextColor(Color.rgb(125, 212, 174))
            })
            addView(TextView(ctx).apply {
                text = "Cut through the noise. See what it's worth."
                textSize = Ios.T_FOOTNOTE
                setTextColor(Color.rgb(192, 211, 219))
                setPadding(0, dp(ctx, 5f), 0, 0)
            })
        }, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f,
        ))
        outer.addView(panel)
        return outer
    }
}
