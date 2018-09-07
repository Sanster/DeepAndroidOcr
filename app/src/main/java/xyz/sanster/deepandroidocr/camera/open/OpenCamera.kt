@file:Suppress("DEPRECATION")

package xyz.sanster.deepandroidocr.camera.open

import android.hardware.Camera

/**
 * Represents an open [Camera] and its metadata, like facing direction and orientation.
 */
// camera APIs
class OpenCamera(private val index: Int, var camera: Camera?, val facing: CameraFacing, val orientation: Int) {

    override fun toString(): String {
        return "Camera #$index : $facing,$orientation"
    }

}