package xyz.sanster.deepandroidocr.model

import android.graphics.RectF
import java.lang.Math.max
import java.lang.Math.min

class Anchor {
    // 将 anchor 组成 pair 时，竖直方向上判断的阈值
    private val MIN_V_OVERLAPS = 0.7
    private val MIN_SIZE_SIM = 0.7

    val x1: Double
    val y1: Double
    val x2: Double
    val y2: Double

    constructor(x1: Double, y1: Double, x2: Double, y2: Double) {
        this.x1 = x1
        this.y1 = y1
        this.x2 = x2
        this.y2 = y2
    }

    constructor(x1: Int, y1: Int, x2: Int, y2: Int) {
        this.x1 = x1.toDouble()
        this.y1 = y1.toDouble()
        this.x2 = x2.toDouble()
        this.y2 = y2.toDouble()
    }

    val width: Double
        get() = x2 - x1

    val height: Double
        get() = y2 - y1

    val ctX: Double
        get() = (x1 + x2) * 0.5

    val ctY: Double
        get() = (y1 + y2) * 0.5

    val rect: RectF
        get() = RectF(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat())

    fun meetVIoU(anchor: Anchor): Boolean {
        return vOverlap(anchor) >= MIN_V_OVERLAPS && sizeSimilarity(anchor) >= MIN_SIZE_SIM
    }

    // 竖直方向上的的重合率，不用实际地相交
    private fun vOverlap(anchor: Anchor): Double {
        val h = min(height, anchor.height)
        val y_min = max(y1, anchor.y1)
        val y_max = min(y2, anchor.y2)
        return max(0.0, y_max - y_min + 1) / h
    }

    private fun sizeSimilarity(anchor: Anchor): Double {
        return min(height, anchor.height) / max(height, anchor.height)
    }

    fun equals(anchor: Anchor): Boolean {
        return x1 == anchor.x1 && x2 == anchor.x2 && y1 == anchor.y1 && y2 == anchor.y2
    }
}


