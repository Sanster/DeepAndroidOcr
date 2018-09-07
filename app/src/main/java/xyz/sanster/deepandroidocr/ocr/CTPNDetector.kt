package xyz.sanster.deepandroidocr.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import xyz.sanster.deepandroidocr.Util
import xyz.sanster.deepandroidocr.model.Anchor
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.Math.*

class CTPNDetector {
    companion object {
        val ANCHOR_HEIGHTS = intArrayOf(11, 16, 23, 33, 48, 68, 97, 139, 198, 283)
        val ANCHOR_WIDTH = 16
        val ANCHOR_BASE_SIZE = 16
        val CNN_STRIDE = 16.0

        val SCALE_LENGTH = 600

        // 给 nms 前获得 topn
        val PRE_NMS_TOPN = 1000 // 12000
        // nms 保留 1000 个结果
        val NMS_THRESH = 0.7
        val TEXT_PROPOSALS_MIN_SCORE = 0.7
        val TEXT_PROPOSALS_NMS_THRESH = 0.2
        val BOX_MIN_SIZE = 8
        // anchor pair 间最远的距离
        val MAX_HORIZONTAL_GAP = 50

        // BGR order, copy from: lib/fast_rcnn/config.py
        // 原本是 BGR 的输入顺序，后来改成了 RGB 输入，但是训练时这个 pixel mean 没有改
        val PIXEL_MEANS = floatArrayOf(102.9801f, 115.9465f, 122.7717f)

        const val FEED_INPUT = "input"
        const val FETCH_RPN_CLS_PROB = "RPN/rpn_cls_prob_reshape"
        const val FETCH_RPN_BBOX_PRED = "RPN/rpn_bbox_pred/Conv2D"
    }

    var TAG = CTPNDetector::class.java.simpleName!!

    private lateinit var context: Context
    private lateinit var tfInterface: TensorFlowInferenceInterface
    val baseAnchors = generateAnchors()

    constructor()

    constructor(context: Context) : this() {
        try {
            this.context = context
            tfInterface = TensorFlowInferenceInterface(context.assets, "mobile_ctpn.pb")
            Log.d(TAG, "model file load success")
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "model file not found")
            e.printStackTrace()
        } catch (e: IOException) {
            Log.e(TAG, "load model file error")
            e.printStackTrace()
        }
    }

    fun detect(data: ByteArray, width: Int, height: Int): Pair<List<Rect>, List<Float>> {
        if (android.os.Debug.isDebuggerConnected()) {
            android.os.Debug.waitForDebugger()
        }

        val start = System.currentTimeMillis()
        val img = Util.yuvToRGBMat(data, width, height)
        Log.d(TAG, "CTPN input image width=${img.width()} height=${img.height()}")
        Log.d(TAG, "YUV to resized Mat & getPixels: ${System.currentTimeMillis() - start} ms")

        return detect(img)
    }

    fun detect(img: Mat): Pair<List<Rect>, List<Float>> {
        // scale image
        var start = System.currentTimeMillis()
        val scale = SCALE_LENGTH.toFloat() / (minOf(img.width(), img.height())).toFloat()

        val scaledWidth = (img.width() * scale).toLong()
        val scaledHeight = (img.height() * scale).toLong()
        val cnnHeight = Math.ceil((scaledHeight / CNN_STRIDE)).toInt()
        val cnnWidth = Math.ceil((scaledWidth / CNN_STRIDE)).toInt()
        Log.d(TAG, "CNN output: $cnnHeight x $cnnWidth")

        val scaledimg = Mat()
        Imgproc.resize(img, scaledimg, Size(scaledWidth.toDouble(), scaledHeight.toDouble()))
        Log.d(TAG, "CTPN scaled image width=${scaledimg.width()} height=${scaledimg.height()}")
        Log.d(TAG, "scale image time: ${System.currentTimeMillis() - start} ms")

        // get pixel float data
        start = System.currentTimeMillis()
        val pixels = getPixels(scaledimg)
        Log.d(TAG, "getPixels: ${System.currentTimeMillis() - start} ms")

        // feed data
        tfInterface.feed(FEED_INPUT, pixels, 1, scaledHeight, scaledWidth, 3)

        // Run RPN net
        start = System.currentTimeMillis()
        tfInterface.run(arrayOf(FETCH_RPN_CLS_PROB, FETCH_RPN_BBOX_PRED))
        Log.d(TAG, "RPN: ${System.currentTimeMillis() - start} ms")

        // fetch data
        val rpnClassScores = FloatArray(cnnHeight * cnnWidth * 2 * baseAnchors.size, { -1f })
        val rpnBBoxDeltas = FloatArray(cnnHeight * cnnWidth * 4 * baseAnchors.size, { -1f })

        tfInterface.fetch(FETCH_RPN_CLS_PROB, rpnClassScores)
        tfInterface.fetch(FETCH_RPN_BBOX_PRED, rpnBBoxDeltas)

        // 获得代表是文字区域的分数，奇数位置
        val scores = rpnClassScores
                .filterIndexed { index, _ -> index % 2 != 0 }
                .map { it }.toFloatArray()

        // 获得图片坐标系下的所有 anchors
        val anchors = locateAnchors(cnnHeight, cnnWidth)

        // 将 rpn box 回归的输出应用到 anchors 上
        val anchorsWithDelta = applyDeltas(anchors, rpnBBoxDeltas)

        // 修剪掉超出图像长宽范围的 anchor
        val clipedAnchors = clipBBox(anchorsWithDelta, scaledWidth.toInt(), scaledHeight.toInt())

        // 过滤掉长宽过小的 anchor
        val filtedAnchors = mutableListOf<Anchor>()
        val filtedScores = mutableListOf<Float>()
        clipedAnchors.forEachIndexed { index, anchor ->
            if (anchor.width > BOX_MIN_SIZE && anchor.height > BOX_MIN_SIZE) {
                filtedAnchors.add(anchor)
                filtedScores.add(scores[index])
            }
        }

        // 获得 topN
        val topNScoresIndex = filtedScores.withIndex()
                .sortedByDescending { (_, s) -> s }
                .take(PRE_NMS_TOPN)
                .map { (i, _) -> i }

        val topNScores = filterByIndices(filtedScores, topNScoresIndex)
        val topNAnchors = filterByIndices(filtedAnchors, topNScoresIndex)

        // 过滤掉 score 小于阈值的 anchor
        var keepIndices = topNScores.withIndex()
                .filter { (_, s) -> s > TEXT_PROPOSALS_MIN_SCORE }
                .map { (i, _) -> i }

        var keepedScores = filterByIndices(topNScores, keepIndices)
        var keepedAnchors = filterByIndices(topNAnchors, keepIndices)

        start = System.currentTimeMillis()
        keepIndices = nms(keepedAnchors, keepedScores, NMS_THRESH)
        Log.d(TAG, "non-max suppression: ${System.currentTimeMillis() - start} ms")

        keepedScores = filterByIndices(keepedScores, keepIndices)
        keepedAnchors = filterByIndices(keepedAnchors, keepIndices)

        // 第二次 nms
        keepIndices = nms(keepedAnchors, keepedScores, TEXT_PROPOSALS_NMS_THRESH)

        keepedScores = filterByIndices(keepedScores, keepIndices)
        keepedAnchors = filterByIndices(keepedAnchors, keepIndices)

        // 寻找 pair neighbour
        val anchorsTable = keepedAnchors.withIndex().groupBy { it.value.x1.toInt() }

        val size = keepedAnchors.size
        val graph = mutableMapOf<Int, Int>()

        for (index in 0 until size) {
            // 向后搜索，找到符合条件的，且分数最大的那个 anchor_j
            val successionIndices = getSuccessions(anchorsTable, keepedAnchors[index], scaledWidth.toInt())
            if (successionIndices.isEmpty()) continue

            val aa = successionIndices.map { Pair(it, keepedScores[it]) }
            val bb = aa.maxBy { it.second }
            val maxScoreSuccessionIndex = bb!!.first


            // 基于 anchor_j 向前搜索
            val precursorIndices = getPrecursors(anchorsTable, keepedAnchors[maxScoreSuccessionIndex])
            if (precursorIndices.isEmpty()) continue

            val maxScorePrecursorIndex =
                    precursorIndices.map { IndexedValue(it, keepedScores[it]) }
                            .maxBy { it.value }!!.index

            val precursorMaxScore = keepedScores[maxScorePrecursorIndex]

            // 如果 anchor 的分数是向前搜索结果中分数最大的，则认为找到了一对 paired neighbour
            if (keepedScores[index] >= precursorMaxScore) {
                graph[index] = maxScoreSuccessionIndex
            }
        }

        // 将位于一行上的 anchors 放到一个 group 里
        val groupedAnchorIndices = mutableListOf<MutableList<Int>>()
        val usedIndices = mutableSetOf<Int>()

        for (i in size - 1 downTo 0) {
            if (!usedIndices.contains(i) && graph.containsKey(i)) {
                groupedAnchorIndices.add(mutableListOf())

                add2Group(groupedAnchorIndices, graph, usedIndices, i)
            }
        }

        // 将一个 group 的 anchors 组成一行，这里的实现和原文中的有些出入，但效果一致
        val rois = mutableListOf<Rect>()
        val outscores = mutableListOf<Float>()
        groupedAnchorIndices.filter { it.size > 3 }
                .forEach { indices ->
                    val lineAnchors = indices.map { keepedAnchors[it] }
                    val score = indices.map { keepedScores[it] }.sum() / indices.size
                    val offset = (lineAnchors[0].x2 - lineAnchors[0].x1) * 0.5
                    val x1 = lineAnchors.minBy { it.x1 }!!.x1 / scale
                    val x2 = lineAnchors.maxBy { it.x2 }!!.x2 / scale
                    val y1 = lineAnchors.minBy { it.y1 }!!.y1 / scale
                    val y2 = lineAnchors.maxBy { it.y2 }!!.y2 / scale
                    val roi = Rect(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt())
                    rois.add(roi)
                    outscores.add(score)
                }

        Log.d(TAG, "Num of the detected text lines: ${rois.size}")

        // Test code start
//        rois.forEach { rect ->
//            Imgproc.rectangle(img,
//                    Point(rect.left.toDouble(), rect.top.toDouble()),
//                    Point(rect.right.toDouble(), rect.bottom.toDouble()), Scalar(0.0, 255.0, 0.0))
//        }
//
//        val tmp = Mat()
//        Imgproc.cvtColor(img, tmp, Imgproc.COLOR_BGR2RGBA, 4)
//
//        val bmp = Bitmap.createBitmap(tmp.cols(), tmp.rows(), Bitmap.Config.ARGB_8888)
//        Utils.matToBitmap(tmp, bmp)
//
//        MediaStore.Images.Media.insertImage(context.contentResolver, bmp, "ctpn test", "ctpn test")
        // Test code end

        return Pair(rois, outscores)
    }

    private fun add2Group(groupedAnchors: MutableList<MutableList<Int>>,
                          graph: MutableMap<Int, Int>,
                          usedIndices: MutableSet<Int>,
                          index: Int) {
        if (!usedIndices.contains(index)) {
            groupedAnchors.last().add(index)
            usedIndices.add(index)
            if (graph.containsKey(index)) {
                add2Group(groupedAnchors, graph, usedIndices, graph.getValue(index))
            }
        }
    }


    // 向前 50px 查找
    private fun getPrecursors(anchorsTable: Map<Int, List<IndexedValue<Anchor>>>,
                              anchor: Anchor): List<Int> {
        val start = anchor.x1.toInt() - 1
        val end = min(start - MAX_HORIZONTAL_GAP, 0)
        val result = mutableListOf<Int>()

        for (i in start downTo end) {
            // 获得处于 x1=i 那一列上的所有 anchor
            val adjAnchors = anchorsTable[i] ?: continue

            adjAnchors.forEach {
                if (anchor.meetVIoU(it.value)) {
                    result.add(it.index)
                }
            }

            if (result.isNotEmpty()) {
                return result
            }
        }
        return result
    }

    // 向后 50px 查找
    private fun getSuccessions(anchorsTable: Map<Int, List<IndexedValue<Anchor>>>,
                               anchor: Anchor, imgWidth: Int): List<Int> {
        val start = anchor.x1.toInt() + 1
        val end = min(start + MAX_HORIZONTAL_GAP, imgWidth)
        val result = mutableListOf<Int>()

        for (i in start until end) {
            // 获得处于 x1=i 那一列上的所有 anchor
            val adjAnchors = anchorsTable[i] ?: continue

            adjAnchors.forEach {
                if (anchor.meetVIoU(it.value)) {
                    result.add(it.index)
                }
            }

            if (result.isNotEmpty()) {
                return result
            }
        }
        return result
    }

    private fun <T> filterByIndices(src: List<T>, indices: List<Int>): List<T> {
        return indices.map { src[it] }
    }

    private fun nms(anchors: List<Anchor>, scores: List<Float>, maxOverLap: Double): List<Int> {
        val keepIndices = mutableListOf<Int>()
        val suppressedIndices = mutableListOf<Int>()
        val n = scores.size

        for (i in 0 until n) {
            if (suppressedIndices.contains(i)) continue
            val maxScorePosition = anchors[i].rect

            keepIndices.add(i)
            for (j in i + 1 until n) {
                if (suppressedIndices.contains(j)) continue

                val position = anchors[j].rect

                val intersection = RectF()
                val intersects = intersection.setIntersect(maxScorePosition, position)

                val intersectArea = intersection.width() * intersection.height()
                val maxScoreArea = maxScorePosition.width() * maxScorePosition.height()
                val area = position.width() * position.height()

                val IoUArea = maxScoreArea + area - intersectArea
                val IoU = intersectArea / IoUArea
                val IoUInMaxScore = intersectArea / maxScoreArea
                val IoUInArea = intersectArea / area

                if (!intersects) continue

                // 过滤掉 IoU 大于 maxOverLap 的区域
                if (IoU > maxOverLap || IoUInMaxScore > 0.95 || IoUInArea > 0.95) {
                    suppressedIndices.add(j)
                }
            }
        }

        return keepIndices
    }

    private fun clipBBox(anchors: List<Anchor>, width: Int, height: Int): List<Anchor> {
        return anchors.map { anchor ->
            Anchor(
                    max(min(anchor.x1, (width - 1).toDouble()), 0.0),
                    max(min(anchor.y1, (height - 1).toDouble()), 0.0),
                    max(min(anchor.x2, (width - 1).toDouble()), 0.0),
                    max(min(anchor.y2, (height - 1).toDouble()), 0.0)
            )
        }
    }

    fun applyDeltas(anchors: MutableList<Anchor>, bboxDeltas: FloatArray): List<Anchor> {
        return anchors.mapIndexed { index, anchor ->
            val dy = bboxDeltas[index * 4 + 1]
            val dh = bboxDeltas[index * 4 + 3]

            val pred_ctr_y = dy * anchor.height + anchor.ctY
            val pred_h = exp(dh.toDouble()) * anchor.height

            val x1 = anchor.x1
            val y1 = pred_ctr_y - 0.5 * pred_h
            val x2 = anchor.x2
            val y2 = pred_ctr_y + 0.5 * pred_h

            Anchor(x1, y1, x2, y2)
        }
    }

    fun generateAnchors(): MutableList<Anchor> {
        val anchors = mutableListOf<Anchor>()
        val baseAnchor = Anchor(0, 0, ANCHOR_BASE_SIZE - 1, ANCHOR_BASE_SIZE - 1)

        ANCHOR_HEIGHTS.forEach { h ->
            anchors.add(Anchor(
                    (baseAnchor.ctX - ANCHOR_WIDTH * 0.5).toInt(),
                    (baseAnchor.ctY - h * 0.5).toInt(),
                    (baseAnchor.ctX + ANCHOR_WIDTH * 0.5).toInt(),
                    (baseAnchor.ctY + h * 0.5).toInt()
            ))
        }

        return anchors
    }

    // 获得在原图片坐标系下未偏移过的 anchor box 坐标
    fun locateAnchors(featureMapHeight: Int, featureMapWidth: Int): MutableList<Anchor> {
        val anchors = mutableListOf<Anchor>()
        for (y in 0 until featureMapHeight) {
            for (x in 0 until featureMapWidth) {
                val shiftedAnchors = baseAnchors.map {
                    Anchor(it.x1 + x * CNN_STRIDE,
                            it.y1 + y * CNN_STRIDE,
                            it.x2 + x * CNN_STRIDE,
                            it.y2 + y * CNN_STRIDE)
                }
                anchors.addAll(shiftedAnchors)
            }
        }

        return anchors
    }

    private fun getPixels(img: Mat): FloatArray {
        val bmp = Bitmap.createBitmap(img.width(), img.height(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(img, bmp)
        val intValues = IntArray(img.width() * img.height())
        bmp.getPixels(intValues, 0, img.width(), 0, 0, img.width(), img.height())

        val floatValues = FloatArray(img.width() * img.height() * 3)
        for (i in intValues.indices) {
            val `val` = intValues[i]
            floatValues[i * 3] = ((`val` shr 16 and 0xFF).toFloat() - PIXEL_MEANS[0])
            floatValues[i * 3 + 1] = ((`val` shr 8 and 0xFF).toFloat() - PIXEL_MEANS[1])
            floatValues[i * 3 + 2] = ((`val` and 0xFF).toFloat() - PIXEL_MEANS[2])
        }

        return floatValues
    }

}
