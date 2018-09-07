package xyz.sanster.deepandroidocr

import android.os.Handler
import android.os.Message
import xyz.sanster.deepandroidocr.R

class ProgressBarHandler(private val activity: MainActivity) : Handler() {
    override fun handleMessage(message: Message?) {
        if (message == null) {
            return
        }
        when (message.what) {
            R.id.show_progress_bar -> activity.showLoadingView()
            R.id.hide_progress_bar -> activity.hideLoadingView()
        }
    }
}