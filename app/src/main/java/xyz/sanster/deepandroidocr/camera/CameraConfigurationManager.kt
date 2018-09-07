/*
 * Copyright (C) 2010 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("DEPRECATION")

package com.google.zxing.client.android.camera

import android.content.Context
import android.graphics.Point
import android.hardware.Camera
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import xyz.sanster.deepandroidocr.camera.CameraConfigurationUtils
import xyz.sanster.deepandroidocr.camera.open.CameraFacing
import xyz.sanster.deepandroidocr.camera.open.OpenCamera


/**
 * A class which deals with reading, parsing, and setting the camera parameters which are used to
 * configure the camera hardware.
 */
internal// camera APIs
class CameraConfigurationManager(private val context: Context) {
    private var cwNeededRotation: Int = 0
    private var cwRotationFromDisplayToCamera: Int = 0
    var screenResolution: Point? = null
    var cameraResolution: Point? = null
    private var bestPreviewSize: Point? = null
    private var previewSizeOnScreen: Point? = null

    /**
     * Reads, one time, values from the camera that are needed by the app.
     */
    fun initFromCameraParameters(camera: OpenCamera) {
        val parameters = camera.camera!!.parameters
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = manager.defaultDisplay

        val displayRotation = display.rotation
        val cwRotationFromNaturalToDisplay: Int
        cwRotationFromNaturalToDisplay = when (displayRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else ->
                // Have seen this return incorrect values like -90
                if (displayRotation % 90 == 0) {
                    (360 + displayRotation) % 360
                } else {
                    throw IllegalArgumentException("Bad rotation: " + displayRotation)
                }
        }
        Log.i(TAG, "Display at: " + cwRotationFromNaturalToDisplay)

        var cwRotationFromNaturalToCamera = camera.orientation
        Log.i(TAG, "Camera at: " + cwRotationFromNaturalToCamera)

        // Still not 100% sure about this. But acts like we need to flip this:
        if (camera.facing === CameraFacing.FRONT) {
            cwRotationFromNaturalToCamera = (360 - cwRotationFromNaturalToCamera) % 360
            Log.i(TAG, "Front camera overriden to: " + cwRotationFromNaturalToCamera)
        }

        /*
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    String overrideRotationString;
    if (camera.getFacing() == CameraFacing.FRONT) {
      overrideRotationString = prefs.getString(PreferencesActivity.KEY_FORCE_CAMERA_ORIENTATION_FRONT, null);
    } else {
      overrideRotationString = prefs.getString(PreferencesActivity.KEY_FORCE_CAMERA_ORIENTATION, null);
    }
    if (overrideRotationString != null && !"-".equals(overrideRotationString)) {
      Log.i(TAG, "Overriding camera manually to " + overrideRotationString);
      cwRotationFromNaturalToCamera = Integer.parseInt(overrideRotationString);
    }
     */

        cwRotationFromDisplayToCamera = (360 + cwRotationFromNaturalToCamera - cwRotationFromNaturalToDisplay) % 360
        Log.i(TAG, "Final display orientation: " + cwRotationFromDisplayToCamera)
        if (camera.facing === CameraFacing.FRONT) {
            Log.i(TAG, "Compensating rotation for front camera")
            cwNeededRotation = (360 - cwRotationFromDisplayToCamera) % 360
        } else {
            cwNeededRotation = cwRotationFromDisplayToCamera
        }
        Log.i(TAG, "Clockwise rotation from display to camera: " + cwNeededRotation)

        val theScreenResolution = Point()
        display.getSize(theScreenResolution)
        screenResolution = theScreenResolution
        Log.i(TAG, "Screen resolution in current orientation: " + screenResolution!!)
        cameraResolution = CameraConfigurationUtils.findBestPreviewSizeValue(parameters, screenResolution!!)
        Log.i(TAG, "Camera resolution: " + cameraResolution!!)
        bestPreviewSize = CameraConfigurationUtils.findBestPreviewSizeValue(parameters, screenResolution!!)
        Log.i(TAG, "Best available preview size: " + bestPreviewSize!!)

        val isScreenPortrait = screenResolution!!.x < screenResolution!!.y
        val isPreviewSizePortrait = bestPreviewSize!!.x < bestPreviewSize!!.y

        if (isScreenPortrait == isPreviewSizePortrait) {
            previewSizeOnScreen = bestPreviewSize
        } else {
            previewSizeOnScreen = Point(bestPreviewSize!!.y, bestPreviewSize!!.x)
        }
        Log.i(TAG, "Preview size on screen: " + previewSizeOnScreen!!)
    }

    fun setDesiredCameraParameters(camera: OpenCamera, safeMode: Boolean) {

        val theCamera = camera.camera!!
        val parameters = theCamera.parameters

        if (parameters == null) {
            Log.w(TAG, "Device error: no camera parameters are available. Proceeding without configuration.")
            return
        }

        Log.i(TAG, "Initial camera parameters: " + parameters.flatten())

        if (safeMode) {
            Log.w(TAG, "In camera config safe mode -- most settings will not be honored")
        }

        Log.d(TAG, "Camera Max zoom: ${parameters.maxZoom}")
//        CameraConfigurationUtils.setZoom(parameters, 1.5)

        CameraConfigurationUtils.setFocus(
                parameters,
                true,
                true,
                safeMode)

        if (!safeMode) {
            // TODO 搞清楚这里的参数影响，参数不启用
            if (false) {
                CameraConfigurationUtils.setVideoStabilization(parameters)
                CameraConfigurationUtils.setFocusArea(parameters)
                CameraConfigurationUtils.setMetering(parameters)
            }
        }

        parameters.setPreviewSize(bestPreviewSize!!.x, bestPreviewSize!!.y)

        theCamera.parameters = parameters

        theCamera.setDisplayOrientation(cwRotationFromDisplayToCamera)

        val afterParameters = theCamera.parameters
        val afterSize = afterParameters.previewSize
        if (afterSize != null && (bestPreviewSize!!.x != afterSize.width || bestPreviewSize!!.y != afterSize.height)) {
            Log.w(TAG, "Camera said it supported preview size " + bestPreviewSize!!.x + 'x' + bestPreviewSize!!.y +
                    ", but after setting it, preview size is " + afterSize.width + 'x' + afterSize.height)
            bestPreviewSize!!.x = afterSize.width
            bestPreviewSize!!.y = afterSize.height
        }
    }

    fun getTorchState(camera: Camera?): Boolean {
        if (camera != null) {
            val parameters = camera.parameters
            if (parameters != null) {
                val flashMode = parameters.flashMode
                return flashMode != null && (Camera.Parameters.FLASH_MODE_ON == flashMode || Camera.Parameters.FLASH_MODE_TORCH == flashMode)
            }
        }
        return false
    }

    fun setTorch(camera: Camera, newSetting: Boolean) {
        val parameters = camera.parameters
        doSetTorch(parameters, newSetting, false)
        camera.parameters = parameters
    }

    private fun doSetTorch(parameters: Camera.Parameters, newSetting: Boolean, safeMode: Boolean) {
        CameraConfigurationUtils.setTorch(parameters, newSetting)
//        if (!safeMode) {
//            CameraConfigurationUtils.setBestExposure(parameters, newSetting)
//        }
    }

    companion object {

        private val TAG = "CameraConfiguration"
    }

}