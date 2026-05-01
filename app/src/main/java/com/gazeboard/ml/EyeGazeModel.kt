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
 * Accelerator chain: NPU+GPU → CPU. All paths use the CompiledModel API
 * (NOT the deprecated Interpreter), satisfying the LiteRT eligibility gate.
 * The active accelerator is displayed in the NPU badge UI.
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

        val candidates = listOf(
            "NPU" to { CompiledModel.create(context.assets, MODEL_ASSET,
                CompiledModel.Options(Accelerator.NPU, Accelerator.GPU)) },
            "CPU" to { CompiledModel.create(context.assets, MODEL_ASSET) }
        )

        var lastException: Exception? = null
        for ((label, factory) in candidates) {
            try {
                val mdl = factory()
                model = mdl
                inputBuffers = mdl.createInputBuffers()
                outputBuffers = mdl.createOutputBuffers()
                acceleratorName = label
                Log.i(TAG, "EyeGaze model loaded on $label via CompiledModel API")
                return
            } catch (e: Exception) {
                Log.w(TAG, "EyeGaze: $label failed (${e.message}), trying next")
                lastException = e
            }
        }
        throw lastException ?: IllegalStateException("All accelerators failed")
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
