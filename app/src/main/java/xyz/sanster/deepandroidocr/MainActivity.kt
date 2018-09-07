package xyz.sanster.deepandroidocr

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import xyz.sanster.deepandroidocr.R
import xyz.sanster.deepandroidocr.camera.CameraManager
import xyz.sanster.deepandroidocr.model.TextResult
import xyz.sanster.deepandroidocr.ocr.CRNNRecoginzer
import xyz.sanster.deepandroidocr.ocr.CTPNDetector
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.OpenCVLoader


class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {
    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private val PERMISSION_REQUEST_CODE: Int = 1

        init {
            if (OpenCVLoader.initDebug()) {
                Log.d(TAG, "OpenCV successfully loaded")
            } else {
                Log.d(TAG, "OpenCV not loaded")
            }
        }
    }

    private var sharedPref: SharedPreferences? = null

    private var hasSurface: Boolean = false
    lateinit var cameraManager: CameraManager
    var handler: CaptureActivityHandler? = null
    private var isProgressing: Boolean = false
    private var roiPaint: Paint = Paint()
    private var flashOn: Boolean = false
    var ip: String? = null

    var categoryModel: Int? = 0 // 默认仅数字

    lateinit var ctpn: CTPNDetector
    lateinit var crnn: CRNNRecoginzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate()")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        hideStatusBar()

        checkPermissionsGranted()

        continueBtn.setOnClickListener { onContinueBtnClick() }
        takeImgBtn.setOnClickListener { onTakeImgBtnClick() }
        flashBtn.setOnClickListener { toggleFlash() }

        category.setOnClickListener { toggleCategory() }

        roiPaint.style = Paint.Style.STROKE
        roiPaint.strokeWidth = 2.0f
        roiPaint.color = Color.GREEN

        sharedPref = getPreferences(Context.MODE_PRIVATE)

        ctpn = CTPNDetector(this)
        crnn = CRNNRecoginzer(this, "simple_mobile_crnn.pb", "chn.txt")
    }

    private fun init() {
//        initCamera()
    }

    private fun onContinueBtnClick() {
        resultView.visibility = View.GONE
        failedView.visibility = View.GONE
        flashBtn.visibility = View.VISIBLE
        viewfinderView.visibility = View.VISIBLE
        menuContainer.visibility = View.VISIBLE
    }

    private fun onTakeImgBtnClick() {
        if (!isProgressing) {
            isProgressing = true
            progressingView.visibility = View.VISIBLE
            viewfinderView.visibility = View.GONE
            startDetect()
        }
    }

    private fun toggleFlash() {
        if (flashOn) {
            flashBtn.setBackgroundResource(R.mipmap.icon_flash_off)
        } else {
            flashBtn.setBackgroundResource(R.mipmap.icon_flash_on)
        }
        flashOn = !flashOn
        cameraManager.setTorch(flashOn)
    }

    private fun toggleCategory() {
        if (categoryContainer.visibility == View.VISIBLE) {
            categoryContainer.visibility = View.GONE
        } else {
            categoryContainer.visibility = View.VISIBLE
        }
    }

    public fun categorySelected(view: View) {
        item0.setBackgroundColor(Color.TRANSPARENT)
        item1.setBackgroundColor(Color.TRANSPARENT)
        view.setBackgroundColor(0x55ffffff)
        categoryModel = view.tag.toString().toInt()
        when (categoryModel) {
            0 -> category.setImageResource(R.mipmap.icon_chinese)
            1 -> category.setImageResource(R.mipmap.icon_english)
        }

        categoryContainer.visibility = View.GONE
    }

    private fun startDetect() {
        Log.d(TAG, "MainActivity.startDetect")
        sendMessage(R.id.start_detect)
    }

    fun handleResult(data: ByteArray?, textResults: ArrayList<TextResult>) {
        isProgressing = false
        viewfinderView.visibility = View.GONE
        progressingView.visibility = View.GONE
        menuContainer.visibility = View.GONE
        flashBtn.visibility = View.GONE
        resultView.visibility = View.VISIBLE

        var detectBitmap: Bitmap? = null
        if (data != null) {
            detectBitmap = BitmapFactory.decodeByteArray(data, 0, data.size, null)
        }

        if (detectBitmap != null) {
            val tempBitmap = Bitmap.createBitmap(detectBitmap.width, detectBitmap.height, detectBitmap.config)
            val canvas = Canvas(tempBitmap)
            canvas.drawBitmap(detectBitmap, 0f, 0f, Paint())

            var allResult = ""
            textResults.forEach { w ->
                allResult += "${w.words}\n"
                canvas.drawRect(w.location, roiPaint)
            }

            detectImg.setImageBitmap(tempBitmap)
            ocrResultText.text = allResult
        }


    }

    fun showFailedView() {
        failedView.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity onResume")
        hideStatusBar()

        // CameraManager must be initialized here, not in onCreate(). This is necessary because we don't
        // want to open the camera driver and measure the screen size if we're going to show the help on
        // first launch. That led to bugs where the scanning rectangle was the wrong size and partially
        // off screen.
        cameraManager = CameraManager(application)

        viewfinderView.cameraManager = cameraManager

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val surfaceHolder = previewSurface.holder
        if (hasSurface) {
            // The activity was paused but not stopped, so the surface still exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            Log.d(TAG, "initCamera() in onResume")
            initCamera(surfaceHolder)
        } else {
            // Install the callback and wait for surfaceCreated() to init the camera.
            Log.d(TAG, "surfaceHolder.addCallback() in onResume")
            surfaceHolder.addCallback(this)
        }
    }

    private fun release() {
        if (handler != null) {
            handler!!.quitSynchronously()
            handler = null
        }
        cameraManager.stopPreview()
        cameraManager.closeDriver()
        if (!hasSurface) {
            previewSurface.holder.removeCallback(this)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity onPause()")
        release()
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        Log.d(TAG, "MainActivity surfaceCreated()")
        if (holder == null) {
            Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!")
        }
        if (!hasSurface) {
            hasSurface = true
            Log.d(TAG, "initCamera() in surfaceCreated()")
            initCamera(holder)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        // do nothing
        Log.d(TAG, "MainActivity surfaceChanged()")
        if (!cameraManager.isOpen) {
            initCamera(holder)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        hasSurface = false
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_REQUEST_CODE -> run {
                val permissionsGranted = grantResults.indices
                        .filter { grantResults[it] == PackageManager.PERMISSION_GRANTED }
                        .map { permissions[it] }

                Log.d(TAG, "on permission request code")
                if (permissionsGranted.isEmpty()) {
                    Log.w(TAG, permissionsGranted.toString())

                    AlertDialog.Builder(this)
                            .setTitle("Permission Alert")
                            .setMessage("Please grant camera permission")
                            .setPositiveButton(android.R.string.yes, { _, _ -> System.exit(-1) }).show()
                } else {
                    init()
                }
            }
            else -> {
            }
        }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkPermissionsGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            init()
            return  // Under older systems, permissions must be granted after installed.
        }

        val permissionsNeeded = mutableListOf<String>()

        if (!isPermissionGranted(Manifest.permission.CAMERA)) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }

        if (!isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (!isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (!isPermissionGranted(Manifest.permission.ACCESS_WIFI_STATE)) {
            permissionsNeeded.add(Manifest.permission.ACCESS_WIFI_STATE)
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            init()
        }
    }

    private fun initCamera(surfaceHolder: SurfaceHolder?) {
        if (surfaceHolder == null) {
            throw IllegalStateException("No SurfaceHolder provided")
        }

        if (cameraManager.isOpen) {
            Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?")
            return
        }

        try {
            cameraManager.openDriver(surfaceHolder)
            // Creating the handler starts the preview, which can also throw a RuntimeException.
            if (handler == null) {
                handler = CaptureActivityHandler(this, cameraManager)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.w(TAG, e)
        }
    }

    fun handleDetectDebug(bitmap: Bitmap?) {
        debugImageView.setImageBitmap(bitmap)
    }

    fun showLoadingView() {
        progressingView.visibility = View.VISIBLE
    }

    fun hideLoadingView() {
        progressingView.visibility = View.GONE
    }

    private fun sendMessage(msg: Int) {
        if (handler != null) {
            val message = Message.obtain(handler, msg)
            handler!!.sendMessage(message)
        }
    }

    private fun hideStatusBar() {
        val decorView = window.decorView
        val uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
        decorView.systemUiVisibility = uiOptions
    }

    fun drawViewfinder() {
        viewfinderView.drawViewfinder()
    }
}
