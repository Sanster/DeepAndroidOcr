package xyz.sanster.deepandroidocr

import android.os.Handler
import android.os.Looper

import java.util.concurrent.CountDownLatch

/**
 * This thread does all the heavy lifting of decoding the images.
 *
 * The code for this class was adapted from the ZXing project: http://code.google.com/p/zxing
 */
internal class DecodeThread(private val activity: MainActivity) : Thread() {
    companion object {
        val DETECT_ROI = "detect_roi"
        val DETECT_BITMAP = "detect_bitmap"
        val API_RESULT = "api_result"
        val WORDS_RESULT = "words_result"
        val FIELDS = "fields"
    }

    private val handlerInitLatch: CountDownLatch = CountDownLatch(1)

    var handler: Handler? = null
        get() {
            try {
                handlerInitLatch.await()
            } catch (ie: InterruptedException) {
                // continue?
            }

            return field
        }

    override fun run() {
        Looper.prepare()
        handler = DecodeHandler(activity)
        handlerInitLatch.countDown()
        Looper.loop()
    }
}

