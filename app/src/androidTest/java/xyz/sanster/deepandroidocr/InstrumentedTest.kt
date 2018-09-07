package xyz.sanster.deepandroidocr

import android.content.Context
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.util.Log
import xyz.sanster.deepandroidocr.ocr.CRNNRecoginzer
import xyz.sanster.deepandroidocr.ocr.CTPNDetector
import org.junit.Assert.assertEquals

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Before
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import java.io.File
import java.io.FileOutputStream
import kotlin.system.measureTimeMillis

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class InstrumentedTest {
    companion object {
        private val TAG = InstrumentedTest::class.java.simpleName
    }

    fun loadAssetImg(context: Context, fileName: String): Mat {
        val imgInputStream = context.assets.open(fileName)
        val f = File(context.cacheDir.absolutePath + "/tmp.jpg")
        val size = imgInputStream.available()
        val buffer = ByteArray(size)
        imgInputStream.read(buffer)
        imgInputStream.close()

        val fos = FileOutputStream(f)
        fos.write(buffer)
        fos.close()

        Log.d(TAG, f.absolutePath)

        val img = Imgcodecs.imread(f.absolutePath)
        return img
    }

    @Before
    fun loadOpencv() {
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV successfully loaded")
        } else {
            Log.e(TAG, "OpenCV not loaded")
        }
    }

    @Test
    fun testCTPN() {
        val appContext = InstrumentationRegistry.getTargetContext()
        val img = loadAssetImg(appContext, "001.jpg")

//        val bitmap = BitmapFactory.decodeStream(imgInputStream)
//        Log.d(TAG, "Width: ${bitmap.width} Height: ${bitmap.height}")
        Log.d(TAG, "Width: ${img.width()} Height: ${img.height()}")

        val ctpnDetector = CTPNDetector(appContext)
        ctpnDetector.TAG = TAG

        ctpnDetector.detect(img)
    }

    @Test
    fun testCRNN() {
        val appContext = InstrumentationRegistry.getTargetContext()
        val crnn = CRNNRecoginzer(appContext, "simple_mobile_crnn.pb", "chn.txt")

//        val imgNames = arrayOf("chn1.jpg", "chn1.jpg", "chn2.jpg", "chn2.jpg", "chn2.jpg")
        val imgNames = arrayOf("chn1.jpg",
                "chn1.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg")

        val labels = arrayOf("它可能是小熊星座那些", "人流散去，垃圾遍地，")

        var totalTime = 0.0
        imgNames.forEachIndexed { i, imgName ->
            val img = loadAssetImg(appContext, imgName)
            val t = measureTimeMillis {
                crnn.recoginze(img)
            }
            if (i != 0) {
                totalTime += t
            }
        }
        Log.d(TAG, "${imgNames.size-1} image time: $totalTime ms")
    }

    @Test
    fun testBatchCRNN() {
        val appContext = InstrumentationRegistry.getTargetContext()
        val crnn = CRNNRecoginzer(appContext, "simple_mobile_crnn.pb", "chn.txt")

//        val imgNames = arrayOf("chn1.jpg", "chn2.jpg", "chn2.jpg", "chn2.jpg")
        val imgNames = arrayOf("chn1.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg", "chn2.jpg",
                "chn2.jpg")
        val labels = arrayOf("它可能是小熊星座那些", "人流散去，垃圾遍地，")

        val imgs = arrayListOf<Mat>()
        imgNames.forEachIndexed { i, imgName ->
            val img = loadAssetImg(appContext, imgName)
            imgs.add(img)
        }

        val runTime = measureTimeMillis {
            val text = crnn.recoginzeBatch(imgs)
        }

        val runTime2 = measureTimeMillis {
            val text = crnn.recoginzeBatch(imgs)
            Log.d(TAG, text)
        }
        Log.d(TAG, "Run time: $runTime2")
        assertEquals("1", "1")
    }

    @Test
    fun testEngCRNN() {
        val appContext = InstrumentationRegistry.getTargetContext()
        val crnn = CRNNRecoginzer(appContext, "raw_eng_crnn.pb", "eng.txt")

        val imgNames = arrayOf("eng1.jpg", "eng2.jpg")
        val labels = arrayOf("Cardus World", "visited newspaper")

        imgNames.forEachIndexed { i, imgName ->
            val img = loadAssetImg(appContext, imgName)
            val text = crnn.recoginze(img)
            Log.d(TAG, text)
            assertEquals(text, labels[i])
        }
    }

    @Test
    fun readCharsFile() {
        val appContext = InstrumentationRegistry.getTargetContext()
        val chars = Util.readChars(appContext, "chn.txt")
        Log.d(TAG, "chars count: ${chars?.size}")
        assertEquals(chars?.size, 5071)
    }

}
