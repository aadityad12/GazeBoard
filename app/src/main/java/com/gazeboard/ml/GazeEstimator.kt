package com.gazeboard.ml

import android.graphics.Bitmap
import android.util.Log

/**
 * Orchestrates the two-stage gaze estimation pipeline:
 *   Stage 1: EyeDetector  — finds eye region in camera frame (ML Kit, CPU)
 *   Stage 2: EyeGazeModel — estimates gaze pitch/yaw from eye crop (CompiledModel, NPU)
 *
 * Output is a smoothed (pitch, yaw) pair in radians, ready for CalibrationEngine.
 * Both pitch and yaw are in the model's native coordinate system:
 *   pitch > 0 = looking down,  pitch < 0 = looking up
 *   yaw   > 0 = looking right, yaw   < 0 = looking left
 *
 * Typical ranges: pitch ∈ [-0.5, 0.5] rad, yaw ∈ [-0.8, 0.8] rad
 *
 * Call [estimate] from a background thread (CameraX analysis executor).
 */
class GazeEstimator {

    // EMA smoothing — α=0.3 balances responsiveness vs jitter
    private val alpha = 0.3f
    private var smoothedPitch = 0f
    private var smoothedYaw   = 0f
    private var hasFirstSample = false

    private val eyeDetector = EyeDetector()

    companion object {
        private const val TAG = "GazeBoard"
    }

    /**
     * Run the full two-stage pipeline on one camera frame.
     *
     * @param bitmap       Full ARGB_8888 camera frame from CameraX (e.g. 640×480, already rotated)
     * @param eyeGazeModel Loaded EyeGazeModel instance (must have load() called)
     *
     * @return [GazeResult] with smoothed pitch/yaw and eye center, or null if no face detected.
     */
    fun estimate(bitmap: Bitmap, eyeGazeModel: EyeGazeModel): GazeResult? {
        val detectResult = eyeDetector.detectAndCrop(bitmap) ?: run {
            Log.d(TAG, "GazeEstimator: no face detected in frame")
            return null
        }

        val angles = eyeGazeModel.runInference(detectResult.buffer) ?: return null

        val smoothed = smooth(angles.pitch, angles.yaw)

        return GazeResult(
            pitch          = smoothed.first,
            yaw            = smoothed.second,
            eyeCenterNormX = detectResult.eyeCenterNormX,
            eyeCenterNormY = detectResult.eyeCenterNormY,
            faceDetectMs   = detectResult.detectMs
        )
    }

    private fun smooth(rawPitch: Float, rawYaw: Float): Pair<Float, Float> {
        if (!hasFirstSample) {
            smoothedPitch = rawPitch
            smoothedYaw   = rawYaw
            hasFirstSample = true
        } else {
            smoothedPitch = alpha * rawPitch + (1f - alpha) * smoothedPitch
            smoothedYaw   = alpha * rawYaw   + (1f - alpha) * smoothedYaw
        }
        return Pair(smoothedPitch, smoothedYaw)
    }

    fun reset() {
        smoothedPitch  = 0f
        smoothedYaw    = 0f
        hasFirstSample = false
        eyeDetector.reset()
    }

    fun close() {
        eyeDetector.close()
    }

    /**
     * Output contract for ViewModel and CalibrationEngine.
     *
     * pitch/yaw are smoothed radians from the NPU model (not yet mapped to screen).
     * eyeCenterNorm{X,Y} are normalized [0,1] coordinates for the PiP overlay.
     * faceDetectMs is ML Kit detection latency for the pipeline stats display.
     */
    data class GazeResult(
        val pitch: Float,
        val yaw: Float,
        val eyeCenterNormX: Float = 0f,
        val eyeCenterNormY: Float = 0f,
        val faceDetectMs: Long = 0L
    )
}
