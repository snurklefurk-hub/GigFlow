package app.gigflow.driver

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** On-device OCR front-end. ML Kit's bundled Latin recognizer keeps offer
 * screenshots on the phone; accessibility text remains the no-image fallback. */
object OcrOfferRecognizer {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    fun recognize(bitmap: Bitmap, pkg: String, currencyCode: String, currencySymbol: String,
                  done: (DetectedOffer?, List<String>) -> Unit) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { block -> block.lines.map { it.text } }
                val nodes = lines.map { OfferParser.FlatNode(it, null, android.graphics.Rect(), false) }
                done(OfferParser.parse(pkg, nodes, OfferParser.extraMoney(currencyCode, currencySymbol), currencySymbol), lines)
            }
            .addOnFailureListener { done(null, emptyList()) }
    }
}
