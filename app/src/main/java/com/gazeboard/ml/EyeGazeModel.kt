package com.gazeboard.ml

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Accelerator
import java.nio.FloatBuffer

/**
 * Wraps the EyeGaze TFLite model via LiteRT CompiledModel API.
 *
 * PERSON A OWNS THIS FILE.
 *
 * Model: qualcomm/EyeGaze (mediapipe_face-tflite-float/eyegaze.tflite)
 *
 * Input:
 *   name: "image"
 *   shape: [1, 96, 160]  — grayscale float32, values in [0, 1]
 *   size:  15,360 floats (96 × 160 × 1)
 *
 * Outputs (3 tensors — all must be allocated, only gaze_pitchyaw is used for gaze):
 *   [0] heatmaps:      [1, 3, 34, 48, 80]  = 391,680 floats (eye landmark heatmaps)
 *   [1] landmarks:     [1, 34, 2]           = 68 floats (34 eye landmark XY positions)
 *   [2] gaze_pitchyaw: [1, 2]               = 2 floats [pitch, yaw] in radians
 *       pitch > 0 = looking down, pitch < 0 = looking up
 *       yaw   > 0 = looking right, yaw < 0 = looking left
 *
 * Typical pitch range: [-0.5, 0.5] rad
 * Typical yaw range:   [-0.8, 0.8] rad
 *
 * Call [runInference] from a background thread (CameraX analysis executor).
 * First launch JIT-compiles for Hexagon NPU (~2–5s); subsequent launches use cache.
 */
class EyeGazeModel(private val context: Context) {

    private var model: CompiledModel? = null

    var acceleratorName: String = "—"
        private set

    var lastInferenceMs: Long = 0L
        private set

    companion object {
        private const val TAG = "GazeBoard"
        private const val MODEL_ASSET = "eyegaze.tflite"

        // Input dimensions (must match model metadata)
        const val INPUT_HEIGHT = 96
        const val INPUT_WIDTH  = 160
        const val INPUT_SIZE   = INPUT_HEIGHT * INPUT_WIDTH  // 15,360

        // Output buffer sizes
        private const val HEATMAPS_SIZE  = 3 * 34 * 48 * 80  // 391,680
        private const val LANDMARKS_SIZE = 34 * 2             // 68
        private const val PITCHYAW_SIZE  = 2

        // Output indices (order defined by TFLite model graph)
        const val OUT_IDX_HEATMAPS  = 0
        const val OUT_IDX_LANDMARKS = 1
        const val OUT_IDX_PITCHYAW  = 2
    }

    /**
     * Load the EyeGaze model with NPU acceleration.
     * Call once on a background thread before the first [runInference].
     *
     * TODO(Person A): Call this from GazeBoardViewModel.onCameraPermissionGranted().
     */
    fun load() {
        try {
            val options = CompiledModel.Options.Builder()
                .setAccelerator(Accelerator.NPU, Accelerator.GPU)
                .build()

            // First launch: LiteRT JIT-compiles for Hexagon NPU and caches.
            model = CompiledModel.create(context.assets, MODEL_ASSET, options)

            acceleratorName = model?.accelerator?.name ?: "UNKNOWN"

            if (acceleratorName == "NPU") {
                Log.i(TAG, "EyeGaze: confirmed NPU execution via CompiledModel API")
            } else {
                Log.w(TAG, "EyeGaze: running on $acceleratorName — expected NPU. " +
                        "If this is first launch, restart after JIT cache warms.")
            }

            // Log tensor shapes to confirm they match metadata.json at runtime
            Log.i(TAG, "EyeGaze model loaded. Input: [1, $INPUT_HEIGHT, $INPUT_WIDTH], " +
                    "Outputs: heatmaps[$HEATMAPS_SIZE], landmarks[$LANDMARKS_SIZE], pitchyaw[$PITCHYAW_SIZE]")

        } catch (e: Exception) {
            Log.e(TAG, "EyeGaze: failed to load model — ${e.message}", e)
            throw e
        }
    }

    /**
     * Run gaze inference on a preprocessed eye crop.
     *
     * @param inputBuffer FloatBuffer of size [INPUT_SIZE] (15,360 floats).
     *        Values must be in [0, 1], layout: row-major [height=96][width=160].
     *        Produce this via [EyePreprocessor.toGrayscaleFloat].
     *
     * @return [GazeAngles] with pitch and yaw in radians, or null if model not loaded.
     *
     * MUST be called from a background thread.
     */
    fun runInference(inputBuffer: FloatBuffer): GazeAngles? {
        val mdl = model ?: run {
            Log.w(TAG, "runInference called before load()")
            return null
        }

        val startMs = SystemClock.elapsedRealtime()

        return try {
            // Allocate all 3 output buffers — CompiledModel.run() requires all outputs
            val heatmapsOut  = FloatArray(HEATMAPS_SIZE)
            val landmarksOut = FloatArray(LANDMARKS_SIZE)
            val pitchYawOut  = FloatArray(PITCHYAW_SIZE)

            mdl.run(
                arrayOf(inputBuffer),
                arrayOf(heatmapsOut, landmarksOut, pitchYawOut)
            )

            lastInferenceMs = SystemClock.elapsedRealtime() - startMs
            Log.d(TAG, "EyeGaze inference: ${lastInferenceMs}ms on $acceleratorName, " +
                    "pitch=${pitchYawOut[0]}, yaw=${pitchYawOut[1]}")

            GazeAngles(pitch = pitchYawOut[0], yaw = pitchYawOut[1])

        } catch (e: Exception) {
            Log.e(TAG, "EyeGaze inference error: ${e.message}", e)
            null
        }
    }

    fun close() {
        // TODO(Person A): model?.close() — confirm CompiledModel close API in LiteRT 2.1.0
        model = null
        Log.i(TAG, "EyeGaze model released")
    }

    /** Gaze angles in radians from the EyeGaze model output. */
    data class GazeAngles(
        val pitch: Float,  // up/down: positive = looking down
        val yaw: Float     // left/right: positive = looking right
    )
}
