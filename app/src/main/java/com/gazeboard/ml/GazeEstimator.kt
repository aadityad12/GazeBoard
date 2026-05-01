package com.gazeboard.ml

import android.graphics.Bitmap
import android.util.Log
import com.gazeboard.calibration.CalibrationEngine

/**
 * Orchestrates the two-stage gaze estimation pipeline:
 *   Stage 1: EyeDetector  — finds eye region in camera frame (ML Kit, CPU)
 *   Stage 2: EyeGazeModel — estimates pitch/yaw from eye crop (CompiledModel, NPU/CPU)
 *
 * Output is a [GazeResult] containing the active quadrant (1-4) and telemetry
 * for the NPU badge. Quadrant mapping requires a calibrated [CalibrationEngine].
 *
 * Call [estimate] from a background thread (CameraX analysis executor).
 */
class GazeEstimator {

    // EMA smoothing — α=0.7 for responsive tracking without excessive jitter
    private val alpha = 0.7f
    private var smoothedPitch = 0f
    private var smoothedYaw   = 0f
    private var hasFirstSample = false

    private val eyeDetector = EyeDetector()

    companion object {
        private const val TAG = "GazeBoard"
    }

    fun estimate(
        bitmap: Bitmap,
        eyeGazeModel: EyeGazeModel,
        calibration: CalibrationEngine
    ): GazeResult {
        val detectResult = eyeDetector.detectAndCrop(bitmap)
            ?: return GazeResult(quadrant = 0, confidence = 0f,
                inferenceMs = eyeGazeModel.lastInferenceMs, accelerator = eyeGazeModel.acceleratorName)

        val angles = eyeGazeModel.runInference(detectResult.buffer)
            ?: return GazeResult(quadrant = 0, confidence = 0f,
                inferenceMs = eyeGazeModel.lastInferenceMs, accelerator = eyeGazeModel.acceleratorName)

        val (sp, sy) = smooth(angles.pitch, angles.yaw)

        val quadrant = if (calibration.isCalibrated) {
            calibration.mapToQuadrant(sp, sy)
        } else {
            calibration.mapToQuadrantUncalibrated(sp, sy)
        }

        return GazeResult(
            quadrant     = quadrant,
            confidence   = 1.0f,
            inferenceMs  = eyeGazeModel.lastInferenceMs,
            accelerator  = eyeGazeModel.acceleratorName,
            rawPitch     = angles.pitch,
            rawYaw       = angles.yaw,
            faceDetectMs = detectResult.detectMs
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

    fun close() = eyeDetector.close()

    /**
     * Output contract between ML pipeline and ViewModel/UI.
     *
     * quadrant: 0 = no face detected, 1..4 = active screen quadrant
     * rawPitch/rawYaw: unsmoothed model output for calibration accumulation
     */
    data class GazeResult(
        val quadrant: Int,
        val confidence: Float,
        val inferenceMs: Long,
        val accelerator: String,
        val rawPitch: Float = 0f,
        val rawYaw: Float = 0f,
        val faceDetectMs: Long = 0L
    )
}
