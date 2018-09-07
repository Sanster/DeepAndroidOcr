package xyz.sanster.deepandroidocr.ocr

import android.content.Context
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import xyz.sanster.deepandroidocr.Util
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.*


class CRNNRecoginzer(context: Context, model: String, charsFile: String) {
    companion object {
        val TAG = CRNNRecoginzer::class.java.simpleName!!

        val IMG_HEIGHT = 32
        val IMG_CHANNEL = 1
        val WIDTH_STRIDE = 8
        val IMAGE_STD = 128
        val IMAGE_MEAN = 128
        val INPUT_IMAGE = "inputs"
        val INPUT_IS_TRAINING = "is_training"
        //        val OUTPUT_NODE = "SparseToDense"
        val OUTPUT_NODE = "output"
    }

    private lateinit var tfInterface: TensorFlowInferenceInterface
    private var converter: LabelConverter = LabelConverter(context, charsFile)

    init {
        try {
            tfInterface = TensorFlowInferenceInterface(context.assets, model)
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "model file not found")
            e.printStackTrace()
        } catch (e: IOException) {
            Log.e(TAG, "load model file error")
            e.printStackTrace()
        }
    }

    // https://stackoverflow.com/questions/46577833/using-bi-lstm-ctc-tensorflow-model-in-android
    // https://github.com/tensorflow/tensorflow/issues/13651
    fun run(data: ByteArray, width: Int, height: Int, rois: List<Rect>): List<String> {
        val out = arrayListOf<String>()
        val grayImg = Util.yuvToGrayMat(data, width, height)

        rois.forEach { roi ->
            val roiImg = Util.getSubMat(grayImg, Util.toOpenCVRect(roi))

            val text = recoginze(roiImg)

            out.add(text)
        }
        return out
    }

    fun recoginze(img: Mat): String {
        val (resizedGrayImg, scaledWidth) = scale2FixHeight(img, img.width(), img.height())
        val pixels = getPixels(resizedGrayImg, scaledWidth)

        tfInterface.feed(INPUT_IMAGE, pixels, 1, IMG_HEIGHT.toLong(), scaledWidth.toLong(), 1)
        tfInterface.feed(INPUT_IS_TRAINING, booleanArrayOf(false))

        val start = System.currentTimeMillis()
        tfInterface.run(arrayOf(OUTPUT_NODE))
        Log.d(TAG, "CRNN Run time: ${System.currentTimeMillis() - start} ms")

        val steps = Math.ceil((scaledWidth.toDouble() / WIDTH_STRIDE)).toInt()
        val result = IntArray(steps, { -1 })
        tfInterface.fetch(OUTPUT_NODE, result)

        return converter.decode(result.toList())
    }

    fun recoginzeBatch(imgs: List<Mat>): String {
//        val (resizedGrayImg, scaledWidth) = scale2FixHeight(img, img.width(), img.height())
        val scaledWidth = 256
        val pixels = FloatArray(scaledWidth * IMG_HEIGHT * IMG_CHANNEL * imgs.size)

        for (i in imgs.indices) {
            val pixels0 = getPixels(imgs[i], scaledWidth)
            val offset = IMG_HEIGHT * IMG_CHANNEL * scaledWidth * i

            for (j in pixels0.indices) {
                pixels[j + offset] = pixels0[j]
            }
        }

        tfInterface.feed(INPUT_IMAGE, pixels, imgs.size.toLong(), IMG_HEIGHT.toLong(), scaledWidth.toLong(), 1)
        tfInterface.feed(INPUT_IS_TRAINING, booleanArrayOf(false))

        val start = System.currentTimeMillis()
        tfInterface.run(arrayOf(OUTPUT_NODE))
        Log.d(TAG, "CRNN Run time: ${System.currentTimeMillis() - start} ms")

        val steps = Math.ceil((scaledWidth.toDouble() / WIDTH_STRIDE)).toInt() * imgs.size
        val result = IntArray(steps, { -1 })
        tfInterface.fetch(OUTPUT_NODE, result)

        return converter.decode(result.toList())
    }

    fun calScaledWidth(width: Int, height: Int): Int {
        val scale = IMG_HEIGHT.toFloat() / height.toFloat()
        return (width * scale).toInt()
    }

    fun scale2FixHeight(img: Mat, width: Int, height: Int): Pair<Mat, Int> {
        val res = Mat()
        val scaledWidth = calScaledWidth(width, height)
        Imgproc.resize(img, res, Size(scaledWidth.toDouble(), IMG_HEIGHT.toDouble()),
                0.0, 0.0, Imgproc.INTER_AREA)
        return Pair(res, scaledWidth)
    }

    private fun getPixels(img: Mat, width: Int): FloatArray {
        val bmp = Bitmap.createBitmap(width, IMG_HEIGHT, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(img, bmp)
        val intValues = IntArray(width * IMG_HEIGHT)
        bmp.getPixels(intValues, 0, width, 0, 0, width, IMG_HEIGHT)

        val floatValues = FloatArray(width * IMG_HEIGHT * IMG_CHANNEL)

        for (i in intValues.indices) {
            val p = intValues[i]
            floatValues[i] = ((p shr 16 and 0xFF).toFloat() - IMAGE_MEAN) / IMAGE_STD
        }

        return floatValues
    }
}
