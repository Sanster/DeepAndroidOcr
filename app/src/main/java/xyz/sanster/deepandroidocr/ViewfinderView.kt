@file:Suppress("DEPRECATION")

package xyz.sanster.deepandroidocr

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import xyz.sanster.deepandroidocr.R
import xyz.sanster.deepandroidocr.camera.CameraManager

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class ViewfinderView// This constructor is used when the class is built from an XML resource.
(context: Context, attrs: AttributeSet) : View(context, attrs) {

    companion object {
        private val CURRENT_POINT_OPACITY = 0xA0
    }

    var cameraManager: CameraManager? = null
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val personOutlinePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val roiPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var resultBitmap: Bitmap? = null
    private val maskColor: Int = resources.getColor(R.color.viewfinder_mask)
    private val detectAreaColor: Int = resources.getColor(R.color.viewfinder_detect_area)
    private val roiColor: Int = resources.getColor(R.color.viewfinder_roi)
    private val resultColor: Int = resources.getColor(R.color.result_view)
    private var vinRoi: Rect = Rect()

    init {
        personOutlinePaint.color = detectAreaColor
        personOutlinePaint.style = Paint.Style.STROKE
        personOutlinePaint.strokeWidth = 3f

        roiPaint.color = roiColor
        roiPaint.style = Paint.Style.STROKE
        roiPaint.strokeWidth = 3f
    }

    @SuppressLint("DrawAllocation")
    public override fun onDraw(canvas: Canvas) {
        if (cameraManager == null) {
            return  // not ready yet, early draw before done configuring
        }
//        var frame = cameraManager!!.getFramingRect()
        cameraManager!!.setManualFramingRect(Utils.getScreenWidth(context), Utils.getScreenHeight(context))
        var frame = cameraManager!!.getFramingRect()
        val previewFrame = cameraManager!!.getFramingRectInPreview()
        if (frame == null || previewFrame == null) {
            return
        }
        val width = canvas.width
        val height = canvas.height

        // 画扫描区域的外部遮罩
        paint.color = if (resultBitmap != null) resultColor else maskColor
        canvas.drawRect(0f, 0f, width.toFloat(), frame.top.toFloat(), paint)
        canvas.drawRect(0f, frame.top.toFloat(), frame.left.toFloat(), (frame.bottom + 1).toFloat(), paint)
        canvas.drawRect((frame.right + 1).toFloat(), frame.top.toFloat(), width.toFloat(), (frame.bottom + 1).toFloat(), paint)
        canvas.drawRect(0f, (frame.bottom + 1).toFloat(), width.toFloat(), height.toFloat(), paint)

        // 画出检测目标区域
//        canvas.drawRect(frame, personOutlinePaint)

        if (resultBitmap != null) {
            // Draw the opaque result bitmap over the scanning rectangle
            paint.alpha = CURRENT_POINT_OPACITY
            canvas.drawBitmap(resultBitmap!!, null, frame, paint)
        } else {
            val roi = vinRoi
            synchronized(roi) {
                canvas.drawRect(roi, roiPaint)
            }
        }
    }

    fun drawViewfinder() {
        val resultBitmap = this.resultBitmap
        this.resultBitmap = null
        resultBitmap?.recycle()
        invalidate()
    }
}

