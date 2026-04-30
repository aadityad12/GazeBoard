package com.gazeboard.ml

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Accelerator
import java.nio.FloatBuffer

/**
 * Wraps the MediaPipe FaceMesh TFLite model via LiteRT CompiledModel API.
 *
 * PERSON A OWNS THIS FILE.
 *
 * Contract: [runInference] must be called from a background thread.
 * Returns FloatArray of size 478*3 = 1434, layout [x0,y0,z0, x1,y1,z1, ...].
 * All coordinates are normalized to [0,1] relative to the 192×192 input.
 *
 * Key indices:
 *   468: left iris center (primary gaze point)
 *   473: right iris center (primary gaze point)
 *   33:  left eye outer corner
 *   133: left eye inner corner
 *   159: left eye upper lid
 *   145: left eye lower lid
 *
 * Model input:  [1, 192, 192, 3] float32, normalized RGB [0,1]
 * Model output: [1, 1434] float32 (or [1, 478, 3] — reshape as needed)
 */
class FaceLandmarkModel(private val context: Context) {

    private var model: CompiledModel? = null

    /** Human-readable name of the active accelerator: "NPU", "GPU", or "CPU". */
    var acceleratorName: String = "—"
        private set

    /** Milliseconds taken by the most recent inference call. */
    var lastInferenceMs: Long = 0L
        private set

    companion object {
        private const val TAG = "GazeBoard"
        private const val MODEL_ASSET = "face_landmark.tflite"
        private const val INPUT_SIZE = 192
        // 478 landmarks × 3 coordinates (x, y, z)
        private const val LANDMARK_COUNT = 478
        private const val OUTPUT_SIZE = LANDMARK_COUNT * 3
    }

    /**
     * Load and initialize the CompiledModel with NPU acceleration.
     * Call once from a background thread before the first [runInference] call.
     *
     * TODO(Person A): Call this from GazeBoardViewModel.onCameraPermissionGranted()
     */
    fun load() {
        try {
            // NPU preferred; GPU as fallback if an op isn't NPU-supported.
            // LiteRT JIT-compiles for the Hexagon NPU on first launch and caches the result.
            // Launch the app once before the demo to warm the compilation cache.
            val options = CompiledModel.Options.Builder()
                .setAccelerator(Accelerator.NPU, Accelerator.GPU)
                .build()

            // First launch JIT-compiles for Hexagon NPU and caches. ~2-5s delay on first run only.
            model = CompiledModel.create(context.assets, MODEL_ASSET, options)

            // Verify we're actually running on the NPU — critical for judging criteria
            acceleratorName = model?.accelerator?.name ?: "UNKNOWN"

            if (acceleratorName == "NPU") {
                Log.i(TAG, "Confirmed NPU execution via CompiledModel API")
            } else {
                Log.w(TAG, "WARNING: Running on $acceleratorName, not NPU! " +
                        "If this is first launch, JIT cache may still be compiling. " +
                        "Restart the app to use the cached NPU model.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}", e)
            // TODO(Person A): Propagate error to ViewModel so UI can show error state
            throw e
        }
    }

    /**
     * Run inference on a bitmap frame.
     *
     * @param bitmap Source bitmap — any size; will be scaled to 192×192 internally.
     * @return FloatArray of size [OUTPUT_SIZE] (1434 = 478×3) with landmark coordinates,
     *         or null if the model is not loaded or inference fails.
     *
     * MUST be called from a background thread (CameraX analysis executor).
     */
    fun runInference(bitmap: Bitmap): FloatArray? {
        val mdl = model ?: run {
            Log.w(TAG, "runInference called before load()")
            return null
        }

        val startMs = SystemClock.elapsedRealtime()

        return try {
            val inputBuffer = preprocessBitmap(bitmap)
            val outputArray = FloatArray(OUTPUT_SIZE)

            // CompiledModel API — NOT the deprecated Interpreter class
            mdl.run(arrayOf(inputBuffer), arrayOf(outputArray))

            lastInferenceMs = SystemClock.elapsedRealtime() - startMs
            Log.d(TAG, "Inference: ${lastInferenceMs}ms on $acceleratorName")

            outputArray
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}", e)
            null
        }
    }

    /**
     * Convert a Bitmap to the model's expected input format:
     * FloatBuffer of shape [1, 192, 192, 3], values normalized to [0,1], RGB order.
     *
     * TODO(Person A): Benchmark this preprocessing step; if it exceeds 2ms,
     * consider using RenderScript or Vulkan for the resize + normalize.
     */
    private fun preprocessBitmap(bitmap: Bitmap): FloatBuffer {
        val scaled = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        }

        val buffer = FloatBuffer.allocate(INPUT_SIZE * INPUT_SIZE * 3)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            // Extract RGB channels, normalize to [0,1]
            buffer.put(((pixel shr 16) and 0xFF) / 255f)  // R
            buffer.put(((pixel shr 8)  and 0xFF) / 255f)  // G
            buffer.put((pixel          and 0xFF) / 255f)  // B
        }

        buffer.rewind()

        if (scaled !== bitmap) scaled.recycle()

        return buffer
    }

    /**
     * Release native model resources. Call from ViewModel.onCleared().
     *
     * TODO(Person A): Verify CompiledModel exposes a close()/release() method;
     * update this call to match the actual API.
     */
    fun close() {
        model?.let {
            // TODO(Person A): it.close() — confirm method name in LiteRT 2.1.0 API
            model = null
            Log.i(TAG, "Model released")
        }
    }
}
