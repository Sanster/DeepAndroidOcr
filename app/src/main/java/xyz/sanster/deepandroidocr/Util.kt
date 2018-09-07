package xyz.sanster.deepandroidocr

import android.content.Context
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import java.io.*


class Util {
    companion object {
        private fun yuvToMat(data: ByteArray, width: Int, height: Int, targetFormat: Int, channel: Int): Mat {
            val yuv = Mat(height + height / 2, width, CvType.CV_8UC1)
            yuv.put(0, 0, data)
            val ret = Mat()
            Imgproc.cvtColor(yuv, ret, targetFormat, channel)
            yuv.release()
            return ret
        }

        fun yuvToGrayMat(data: ByteArray, width: Int, height: Int): Mat {
            return yuvToMat(data, width, height, Imgproc.COLOR_YUV2GRAY_NV21, 1)
        }

        fun yuvToRGBMat(data: ByteArray, width: Int, height: Int): Mat {
            return yuvToMat(data, width, height, Imgproc.COLOR_YUV2RGB_NV21, 3)
        }

        fun yuvToBGRMat(data: ByteArray, width: Int, height: Int): Mat {
            return yuvToMat(data, width, height, Imgproc.COLOR_YUV2BGR_NV21, 3)
        }

        /**
         * 从大图中根据矩形信息抠出小图
         *
         * @param parentMat 大图矩阵
         * @param rect      矩形信息
         * @return 抠出的矩形图像矩阵
         */
        fun getSubMat(parentMat: Mat, rect: Rect): Mat {
            return try {
                Mat(parentMat, rect)
            } catch (e: Exception) {
                parentMat
            }
        }

        fun toOpenCVRect(rect: android.graphics.Rect): Rect {
            return Rect(rect.left, rect.top, rect.width(), rect.height())
        }

        fun mergeRects(rects: MutableList<android.graphics.Rect>): android.graphics.Rect {
            var min_x = Int.MAX_VALUE
            var min_y = Int.MAX_VALUE
            var max_x = 0
            var max_y = 0
            for (rect in rects) {
                val p1_x = rect.left
                val p1_y = rect.top
                val p2_x = rect.right
                val p2_y = rect.bottom

                if (p1_x < min_x) {
                    min_x = p1_x
                }

                if (p1_y < min_y) {
                    min_y = p1_y
                }

                if (p2_x > max_x) {
                    max_x = p2_x
                }

                if (p2_y > max_y) {
                    max_y = p2_y
                }
            }

            return android.graphics.Rect(min_x, min_y, max_x, max_y)
        }

        @Throws(Exception::class)
        fun readChars(context: Context, fileName: String): List<Char>? {
            val inStream = context.assets.open(fileName)

            var chars: List<Char>? = null
            var reader: InputStreamReader? = null

            try {
                reader = InputStreamReader(inStream)
                val lines = reader.readLines()
                chars = lines.map { it[0] }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                try {
                    reader?.close()
                    inStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            return chars
        }

    }
}
