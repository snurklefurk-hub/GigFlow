package app.gigflow.driver

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Private, compact regression cases. We retain corrected fields and an image
 * digest only — never the screenshot or its potentially sensitive addresses. */
object LocalRegressionCorpus {
    private const val KEY = "ocr_regression_corpus"
    private const val MAX = 100

    fun add(context: Context, imageBytes: ByteArray, offer: DetectedOffer) {
        val prefs = context.getSharedPreferences("gigflow_training", Context.MODE_PRIVATE)
        val old = JSONArray(prefs.getString(KEY, "[]"))
        val item = JSONObject()
            .put("digest", MessageDigest.getInstance("SHA-256").digest(imageBytes).joinToString("") { "%02x".format(it) })
            .put("pkg", offer.appPackage).put("payout", offer.payoutCents)
            .put("km", offer.distanceKm).put("min", offer.durationMin)
            .put("pickupKm", offer.extraDistanceKm).put("currency", offer.currencySymbol)
        val next = JSONArray().put(item)
        for (i in 0 until minOf(old.length(), MAX - 1)) next.put(old.get(i))
        prefs.edit().putString(KEY, next.toString()).apply()
    }

    fun size(context: Context): Int = JSONArray(context.getSharedPreferences("gigflow_training", Context.MODE_PRIVATE)
        .getString(KEY, "[]")).length()
}
