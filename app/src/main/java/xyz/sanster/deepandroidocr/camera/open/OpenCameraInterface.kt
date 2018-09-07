@file:Suppress("DEPRECATION")

package xyz.sanster.deepandroidocr.camera.open

import android.hardware.Camera
import android.util.Log

/**
 * Abstraction over the [Camera] API that helps open them and return their metadata.
 */
// camera APIs
object OpenCameraInterface {

    private val TAG = OpenCameraInterface::class.java.name

    /** For [.open], means no preference for which camera to open.  */
    val NO_REQUESTED_CAMERA = -1

    /**
     * Opens the requested camera with [Camera.open], if one exists.
     *
     * @param cameraId camera ID of the camera to use. A negative value
     * or [.NO_REQUESTED_CAMERA] means "no preference", in which case a rear-facing
     * camera is returned if possible or else any camera
     * @return handle to [OpenCamera] that was opened
     */
    fun open(cameraId: Int): OpenCamera? {

        val numCameras = Camera.getNumberOfCameras()
        if (numCameras == 0) {
            Log.w(TAG, "No cameras!")
            return null
        }

        val explicitRequest = cameraId >= 0

        var selectedCameraInfo: Camera.CameraInfo? = null
        var index: Int
        if (explicitRequest) {
            index = cameraId
            selectedCameraInfo = Camera.CameraInfo()
            Camera.getCameraInfo(index, selectedCameraInfo)
        } else {
            index = 0
            while (index < numCameras) {
                val cameraInfo = Camera.CameraInfo()
                Camera.getCameraInfo(index, cameraInfo)
                val reportedFacing = CameraFacing.values()[cameraInfo.facing]
                if (reportedFacing === CameraFacing.BACK) {
                    selectedCameraInfo = cameraInfo
                    break
                }
                index++
            }
        }

        val camera: Camera?
        if (index < numCameras) {
            Log.i(TAG, "Opening camera #" + index)
            camera = Camera.open(index)
        } else {
            if (explicitRequest) {
                Log.w(TAG, "Requested camera does not exist: " + cameraId)
                camera = null
            } else {
                Log.i(TAG, "No camera facing " + CameraFacing.BACK + "; returning camera #0")
                camera = Camera.open(0)
                selectedCameraInfo = Camera.CameraInfo()
                Camera.getCameraInfo(0, selectedCameraInfo)
            }
        }

        return if (camera == null) {
            null
        } else OpenCamera(index,
                camera,
                CameraFacing.values()[selectedCameraInfo!!.facing],
                selectedCameraInfo.orientation)
    }

}