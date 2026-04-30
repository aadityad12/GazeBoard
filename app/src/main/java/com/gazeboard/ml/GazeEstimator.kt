package com.gazeboard.ml

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log

/**
 * Orchestrates the two-stage gaze estimation pipeline:
 *   Stage 1: EyeDetector  — finds eye region in camera frame (Android FaceDetector, CPU)
 *   Stage 2: EyeGazeModel — estimates gaze pitch/yaw from eye crop (CompiledModel, NPU)
 *
 * PERSON B OWNS THIS FILE.
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

    // EMA smoothing — higher alpha = more responsive, lower = smoother
    // Tunable at Hour 8: try 0.2 (smooth) or 0.4 (responsive) if 0.3 isn't right
    private val alpha = 0.3f
    private var smoothedPitch = 0f
    private var smoothedYaw   = 0f
    private var hasFirstSample = false

    private val eyeDetector  = EyeDetector()

    companion object {
        private const val TAG = "GazeBoard"
    }

    /**
     * Run the full two-stage pipeline on one camera frame.
     *
     * @param bitmap    Full ARGB_8888 camera frame from CameraX (e.g. 640×480)
     * @param eyeGazeModel Loaded EyeGazeModel instance (must have load() called)
     *
     * @return [GazeResult] with smoothed pitch/yaw, or null if no face was detected.
     */
    fun estimate(bitmap: Bitmap, eyeGazeModel: EyeGazeModel): GazeResult? {
        // Stage 1: Detect eye region and preprocess to 96×160 grayscale FloatBuffer
        val eyeBuffer = eyeDetector.detectAndCrop(bitmap) ?: run {
            Log.d(TAG, "GazeEstimator: no face detected in frame")
            return null
        }

        // Stage 2: NPU inference → pitch, yaw
        val angles = eyeGazeModel.runInference(eyeBuffer) ?: return null

        // EMA smoothing
        val smoothed = smooth(angles.pitch, angles.yaw)

        return GazeResult(
            pitch = smoothed.first,
            yaw   = smoothed.second
        )
    }

    /**
     * Exponential moving average applied separately to pitch and yaw.
     * On the very first sample, initialize the filter to the raw value (no lag).
     */
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

    /**
     * Output contract to ViewModel / CalibrationEngine.
     *
     * pitch, yaw in radians (model's native output — not yet mapped to screen).
     * CalibrationEngine maps (pitch, yaw) → screen pixel coordinates.
     */
    data class GazeResult(
        val pitch: Float,
        val yaw: Float
    )
}
