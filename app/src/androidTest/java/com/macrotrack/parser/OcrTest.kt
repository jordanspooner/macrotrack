package com.macrotrack.parser

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OcrTest {

    @Test
    fun ocrAllLabels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val files = listOf(
            "leerdammer-light.jpg",
            "sabra-houmous-extra.jpg",
            "sainsburys-soured-cream.jpg",
            "british-chicken-breast.jpg",
            "tesco-semi-skimmed-milk.jpg",
            "pepsi-max-cherry.jpg",
            "lurpak-spreadable.jpg",
            "morrisons-lemon-juice.jpg",
            "gochujang.jpg",
            "sainsburys-lemon-curd.jpg",
            "frenchs-yellow-mustard.jpg",
            "goodness-shakes-chocolate.jpg",
            "roses-lime-cordial.jpg",
            "skipjack-tuna-brine.jpg",
            "lidl-cooking-oil-spray.jpg"
        )

        for (filename in files) {
            val path = "/data/local/tmp/labels/$filename"
            val file = java.io.File(path)
            if (!file.exists()) {
                Log.e("OCR_TEST", "FILE NOT FOUND: $path")
                continue
            }
            val bitmap = BitmapFactory.decodeFile(path)
            if (bitmap == null) {
                Log.e("OCR_TEST", "COULD NOT DECODE: $filename")
                continue
            }
            Log.d("OCR_TEST", "=== $filename (${bitmap.width}x${bitmap.height}) ===")
            val image = InputImage.fromBitmap(bitmap, 0)
            val latch = CountDownLatch(1)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    for (block in visionText.textBlocks) {
                        val bb = block.boundingBox
                        Log.d("OCR_TEST", "BLOCK [${bb?.left},${bb?.top} ${bb?.right},${bb?.bottom}]")
                        for (line in block.lines) {
                            val lb = line.boundingBox
                            Log.d("OCR_TEST", "  LINE [${lb?.left},${lb?.top} ${lb?.right},${lb?.bottom}] \"${line.text}\"")
                            for (element in line.elements) {
                                val eb = element.boundingBox
                                Log.d("OCR_TEST", "    ELEM [${eb?.left},${eb?.top} ${eb?.right},${eb?.bottom}] \"${element.text}\"")
                            }
                        }
                    }
                    Log.d("OCR_TEST", "=== END $filename ===")
                    latch.countDown()
                }
                .addOnFailureListener { e ->
                    Log.e("OCR_TEST", "FAILED $filename: ${e.message}")
                    latch.countDown()
                }
            latch.await(30, TimeUnit.SECONDS)
        }
    }
}
