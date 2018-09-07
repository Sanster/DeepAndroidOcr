@file:Suppress("DEPRECATION")

package xyz.sanster.deepandroidocr.camera

import android.content.Context
import android.hardware.Camera
import android.os.AsyncTask
import android.util.Log

import java.util.concurrent.RejectedExecutionException

internal// camera APIs
class AutoFocusManager(context: Context, private val camera: Camera) : Camera.AutoFocusCallback {

    companion object {

        private val TAG = AutoFocusManager::class.java.simpleName

        private val AUTO_FOCUS_INTERVAL_MS = 2000L
    }

    private var stopped: Boolean = false
    private var focusing: Boolean = false
    private var useAutoFocus: Boolean = true
    private var outstandingTask: AsyncTask<*, *, *>? = null

    init {
        val currentFocusMode = camera.parameters.focusMode
        Log.i(TAG, "Current focus mode '$currentFocusMode'; use auto focus? $useAutoFocus")
        start()
    }

    @Synchronized
    override fun onAutoFocus(success: Boolean, theCamera: Camera) {
        focusing = false
        autoFocusAgainLater()
    }

    @Synchronized
    private fun autoFocusAgainLater() {
        if (!stopped && outstandingTask == null) {
            val newTask = AutoFocusTask()
            try {
//                timer.schedule(newTask)
                newTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                outstandingTask = newTask
            } catch (ree: RejectedExecutionException) {
                Log.w(TAG, "Could not request auto focus", ree)
            }

        }
    }

    @Synchronized
    fun start() {
        if (useAutoFocus) {
            outstandingTask = null
            if (!stopped && !focusing) {
                try {
                    camera.autoFocus(this)
                    focusing = true
                } catch (re: RuntimeException) {
                    // Have heard RuntimeException reported in Android 4.0.x+; continue?
                    Log.e(TAG, "Unexpected exception while focusing", re)
                    // Try again later to keep cycle going
                    autoFocusAgainLater()
                }
            }
        }
    }

    @Synchronized
    private fun cancelOutstandingTask() {
        if (outstandingTask != null) {
            if (outstandingTask!!.status != AsyncTask.Status.FINISHED) {
                outstandingTask!!.cancel(true)
            }
            outstandingTask = null
        }
    }

    @Synchronized
    fun stop() {
        stopped = true
        if (useAutoFocus) {
            cancelOutstandingTask()
            // Doesn't hurt to call this even if not focusing
            try {
                camera.cancelAutoFocus()
            } catch (re: RuntimeException) {
                // Have heard RuntimeException reported in Android 4.0.x+; continue?
                Log.w(TAG, "Unexpected exception while cancelling focusing", re)
            }

        }
    }

    private inner class AutoFocusTask : AsyncTask<Any, Any, Any>() {
        override fun doInBackground(vararg voids: Any): Any? {
            try {
                Thread.sleep(AUTO_FOCUS_INTERVAL_MS)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            start()
            return null
        }
    }

}