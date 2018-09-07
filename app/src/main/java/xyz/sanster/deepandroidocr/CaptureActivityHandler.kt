package xyz.sanster.deepandroidocr

import android.graphics.BitmapFactory

import android.graphics.Bitmap
import android.os.Handler
import android.os.Message
import android.util.Log
import xyz.sanster.deepandroidocr.R
import xyz.sanster.deepandroidocr.camera.CameraManager
import xyz.sanster.deepandroidocr.model.TextResult


/**
 * This class handles all the messaging which comprises the state machine for capture.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class CaptureActivityHandler internal constructor(private val activity: MainActivity,
                                                  private val cameraManager: CameraManager) : Handler() {
    private var state: State? = null
    private var decodeThread: DecodeThread = DecodeThread(activity)

    companion object {
        private val TAG = CaptureActivityHandler::class.java.simpleName
    }

    private enum class State {
        PREVIEW,
        SUCCESS,
        DONE
    }

    init {
        decodeThread.start()
        state = State.SUCCESS
        cameraManager.startPreview()
    }

    override fun handleMessage(message: Message) {
        Log.d(TAG, "Capture ActivityHandler handleMessage. message.what=${message.what}")
        when (message.what) {
            R.id.start_detect -> startDetect()
            R.id.detect_succeeded -> {
                state = State.SUCCESS

                val bundle = message.data
                if (bundle != null) {
                    val compressedBitmap = bundle.getByteArray(DecodeThread.DETECT_BITMAP)
                    val wordsResults: ArrayList<TextResult> = bundle.getParcelableArrayList(DecodeThread.WORDS_RESULT)

                    activity.handleResult(compressedBitmap, wordsResults)
                }

            }
            R.id.detect_failed -> {
                // We're decoding as fast as possible, so when one decode fails, start another.
//                state = State.PREVIEW
//                cameraManager.requestPreviewFrame(decodeThread.handler!!, R.id.old_detect)
                activity.showFailedView()
            }
            R.id.detect_debug -> {
                val bundle = message.data
                var debugBitmap: Bitmap? = null
                if (bundle != null) {
                    val compressedBitmap = bundle.getByteArray(DecodeThread.DETECT_BITMAP)
                    if (compressedBitmap != null) {
                        debugBitmap = BitmapFactory.decodeByteArray(compressedBitmap, 0, compressedBitmap.size, null)
                        debugBitmap = debugBitmap!!.copy(Bitmap.Config.ARGB_8888, true)
                    }

                    activity.handleDetectDebug(debugBitmap)
                }
            }
        }
    }

    fun quitSynchronously() {
        state = State.DONE
        cameraManager.stopPreview()
        val quit = Message.obtain(decodeThread.handler, R.id.quit)
        quit.sendToTarget()
        try {
            // Wait at most half a second; should be enough time, and onPause() will timeout quickly
            decodeThread.join(500L)
        } catch (e: InterruptedException) {
            // continue
        }

        // Be absolutely sure we don't send any queued up messages
        removeMessages(R.id.detect_succeeded)
        removeMessages(R.id.detect_failed)
    }

    private fun startDetect() {
        Log.d(TAG, "startDetect, state=$state")
        if (state == State.SUCCESS) {
            state = State.PREVIEW
            cameraManager.requestPreviewFrame(decodeThread.handler!!, R.id.detect)
            activity.drawViewfinder()
        }
    }

}

