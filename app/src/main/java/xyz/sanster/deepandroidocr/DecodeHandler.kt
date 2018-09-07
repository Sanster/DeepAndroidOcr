package xyz.sanster.deepandroidocr

import android.graphics.*

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import xyz.sanster.deepandroidocr.camera.PlanarYUVLuminanceSource
import xyz.sanster.deepandroidocr.model.TextResult

import java.io.ByteArrayOutputStream


internal class DecodeHandler(private val activity: MainActivity) : Handler() {
    private var running = true

    companion object {
        private val TAG = DecodeHandler::class.java.simpleName
    }

    override fun handleMessage(message: Message?) {
        if (message == null || !running) {
            return
        }
        when (message.what) {
            R.id.detect -> {
                detect(message.obj as ByteArray, message.arg1, message.arg2)
            }
            R.id.quit -> {
                running = false
                Looper.myLooper()!!.quit()
            }
        }
    }

    /**
     * Decode the data within the viewfinder rectangle, and time how long it took. For efficiency,
     * reuse the same reader objects from one old_detect to the next.
     *
     * @param data   The YUV preview frame.
     * @param width  The width of the preview frame.
     * @param height The height of the preview frame.
     */
    private fun detect(data: ByteArray, width: Int, height: Int) {
        val handler = activity.handler
        val roi = activity.cameraManager.getFramingRectInPreview()

        if (roi == null) {
            sendFailedMessage(handler)
            return
        }

        Log.d(TAG, "Detect one frame. roi width: ${roi.width()} height: ${roi.height()}")
        val roiData = getRoiByteJpeg(data, width, height, roi)

        // 修正实际的 NV21 data
        val size = Utils.getCorrectHeightAndWidth(roi.width(), roi.height())
        roi.bottom = roi.top + size.y
        roi.right = roi.left + size.x
        Log.d(TAG, "roi width=${roi.width()} height=${roi.height()}")

        if (roiData == null) {
            sendFailedMessage(handler)
            return
        }

        val wordsResults = arrayListOf<TextResult>()
        try {
            var start = System.currentTimeMillis()
//            val textRois = activity.ctpn.detect(roidData, roi.width(), roi.height())
            val (textRois, scores) = activity.ctpn.detect(data, width, height)
            Log.d(TAG, "CTPN total time: ${System.currentTimeMillis() - start} ms")

            start = System.currentTimeMillis()
            val texts = when (activity.categoryModel) {
                activity.CHN -> activity.chnCrnn.run(data, width, height, textRois)
                activity.ENG -> activity.engCrnn.run(data, width, height, textRois)
                else -> {
                    activity.chnCrnn.run(data, width, height, textRois)
                }
            }

            Log.d(TAG, "CRNN total time: ${System.currentTimeMillis() - start} ms")

            textRois.forEachIndexed { index, rect ->
                wordsResults.add(TextResult(rect, texts[index], scores[index]))
            }

            wordsResults.sortBy { it.location.centerY() }

        } catch (e: Exception) {
            e.printStackTrace()
            sendFailedMessage(handler)
        }

        if (handler != null) {
            sendDetectResult(handler, roiData, roi, wordsResults)
        }
    }

    private fun getRoiByteJpeg(data: ByteArray, width: Int, height: Int, roi: Rect): ByteArray? {
        val img = YuvImage(data, ImageFormat.NV21, width, height, null)
        val roiJpeg = ByteArrayOutputStream()
        img.compressToJpeg(roi, 100, roiJpeg)
        val roiJpegBytes = roiJpeg.toByteArray()

        return roiJpegBytes
    }

    private fun sendFailedMessage(handler: CaptureActivityHandler?) {
        if (handler != null) {
            val message = Message.obtain(handler, R.id.detect_failed)
            message.sendToTarget()
        }
    }

    private fun sendDetectResult(handler: CaptureActivityHandler,
                                 roiJpeg: ByteArray,
                                 roi: Rect,
                                 wordsResults: ArrayList<TextResult>) {
        val message = Message.obtain(handler, R.id.detect_succeeded)

        val bundle = Bundle()

        bundle.putByteArray(DecodeThread.DETECT_BITMAP, roiJpeg)
        bundle.putParcelable(DecodeThread.DETECT_ROI, roi)
        bundle.putParcelableArrayList(DecodeThread.WORDS_RESULT, wordsResults)

        message.data = bundle
        message.sendToTarget()
    }

    private fun bundleThumbnail(source: PlanarYUVLuminanceSource, bundle: Bundle) {
        val pixels = source.renderThumbnail()
        val width = source.thumbnailWidth
        val height = source.thumbnailHeight
        val bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out)
        bundle.putByteArray(DecodeThread.DETECT_BITMAP, out.toByteArray())
    }

}


