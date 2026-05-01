package com.gazeboard.ml

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import java.nio.FloatBuffer

/**
 * Wraps the EyeGaze TFLite model via LiteRT CompiledModel API.
 *
 * Model: qualcomm/EyeGaze (eyegaze.tflite)
 * Input:  [1, 96, 160]  float32 grayscale [0, 1]
 * Output 0 heatmaps:      [1, 3, 34, 48, 80]
 * Output 1 landmarks:     [1, 34, 2]
 * Output 2 gaze_pitchyaw: [1, 2] — pitch, yaw in radians
 *
 * NPU only — no CPU/GPU fallback. If NPU is unavailable, load() throws and the
 * ViewModel transitions to AppState.ModelLoadError. All paths use CompiledModel API
 * (NOT the deprecated Interpreter), satisfying the LiteRT eligibility gate.
 * A warm-up inference is run at load time to trigger Hexagon JIT compilation.
 */
class EyeGazeModel(private val context: Context) {

    private var model: CompiledModel? = null
    private var inputBuffers: List<TensorBuffer>? = null
    private var outputBuffers: List<TensorBuffer>? = null

    var acceleratorName: String = "—"
        private set

    var lastInferenceMs: Long = 0L
        private set

    companion object {
        private const val TAG = "GazeBoard"
        private const val MODEL_ASSET = "eyegaze.tflite"

        const val INPUT_HEIGHT = 96
        const val INPUT_WIDTH  = 160
        const val INPUT_SIZE   = INPUT_HEIGHT * INPUT_WIDTH  // 15,360

        private const val OUT_IDX_PITCHYAW = 2
    }

    fun load() {
        close()

        // NPU-only: no CPU/GPU fallback. If NPU is unavailable the exception
        // propagates to GazeBoardViewModel which shows ModelLoadError instead of
        // silently degrading to CPU inference.
        val mdl = CompiledModel.create(
            context.assets,
            MODEL_ASSET,
            CompiledModel.Options(Accelerator.NPU)
        )
        model = mdl
        val inputs  = mdl.createInputBuffers()
        val outputs = mdl.createOutputBuffers()
        inputBuffers  = inputs
        outputBuffers = outputs
        acceleratorName = "NPU"
        Log.i(TAG, "EyeGaze model loaded on NPU via CompiledModel API")

        // Warm-up: triggers LiteRT JIT compilation for the Hexagon DSP and
        // caches the compiled kernel to disk. Subsequent runs skip recompilation.
        try {
            val warmInput = FloatArray(INPUT_SIZE) { 0f }
            inputs[0].writeFloat(warmInput)
            val t0 = SystemClock.elapsedRealtime()
            mdl.run(inputs, outputs)
            Log.i(TAG, "NPU JIT warm-up complete: ${SystemClock.elapsedRealtime() - t0}ms")
        } catch (e: Exception) {
            Log.w(TAG, "Warm-up failed (non-fatal): ${e.message}")
        }
    }

    fun runInference(inputBuffer: FloatBuffer): GazeAngles? {
        val mdl = model ?: return null
        val inputs = inputBuffers ?: return null
        val outputs = outputBuffers ?: return null

        return try {
            val inputArray = FloatArray(INPUT_SIZE)
            inputBuffer.rewind()
            inputBuffer.get(inputArray)
            inputs[0].writeFloat(inputArray)

            val startMs = SystemClock.elapsedRealtime()
            mdl.run(inputs, outputs)
            lastInferenceMs = SystemClock.elapsedRealtime() - startMs

            val pitchYaw = outputs[OUT_IDX_PITCHYAW].readFloat()
            Log.d(TAG, "EyeGaze ${lastInferenceMs}ms — pitch=${pitchYaw[0]}, yaw=${pitchYaw[1]}")
            GazeAngles(pitch = pitchYaw[0], yaw = pitchYaw[1])
        } catch (e: Exception) {
            Log.e(TAG, "EyeGaze inference error: ${e.message}", e)
            null
        }
    }

    fun close() {
        inputBuffers?.forEach { runCatching { it.close() } }
        outputBuffers?.forEach { runCatching { it.close() } }
        runCatching { model?.close() }
        model = null
        inputBuffers = null
        outputBuffers = null
    }

    data class GazeAngles(val pitch: Float, val yaw: Float)
}
