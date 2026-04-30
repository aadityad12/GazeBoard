package com.gazeboard.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.gazeboard.ml.EyeGazeModel
import com.gazeboard.ml.GazeEstimator
import com.gazeboard.state.GazeBoardViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages the CameraX ImageAnalysis pipeline.
 *
 * PERSON B OWNS THIS FILE.
 *
 * Frame pipeline:
 *   ImageProxy (ARGB_8888) → toBitmap() → GazeEstimator.estimate()
 *     → EyeDetector [Android FaceDetector, CPU] → eye crop FloatBuffer
 *     → EyeGazeModel [CompiledModel, NPU] → pitch, yaw
 *   → GazeBoardViewModel.onGazeUpdate()
 *
 * Uses STRATEGY_KEEP_ONLY_LATEST to drop stale frames and keep pipeline latency bounded.
 * Never blocks the main thread — all processing runs on [inferenceExecutor].
 */
class CameraManager(
    private val context: Context,
    private val eyeGazeModel: EyeGazeModel,
    private val gazeEstimator: GazeEstimator,
    private val viewModel: GazeBoardViewModel
) {

    private val inferenceExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Exposed so BoardScreen can attach a PreviewView surface provider
    val preview: Preview = Preview.Builder().build()

    companion object {
        private const val TAG = "GazeBoard"
    }

    /**
     * Bind the CameraX analysis use case to the given lifecycle.
     * Safe to call from the main thread.
     *
     * TODO(Person B): Call this from GazeBoardViewModel.onCameraPermissionGranted().
     */
    fun start(lifecycleOwner: LifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({
            val cameraProvider = providerFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setTargetRotation(Surface.ROTATION_0)
                // KEEP_ONLY_LATEST: discard queued frames so we always process the freshest.
                // Prevents memory growth when inference takes longer than frame interval.
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(inferenceExecutor) { imageProxy ->
                processFrame(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )
                Log.i(TAG, "CameraX ImageAnalysis bound (640×480, RGBA_8888, KEEP_ONLY_LATEST)")
            } catch (e: Exception) {
                Log.e(TAG, "CameraX bind failed: ${e.message}", e)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Process a single camera frame through the full gaze pipeline.
     *
     * MUST call imageProxy.close() before returning — failure stalls the camera pipeline.
     */
    private fun processFrame(imageProxy: ImageProxy) {
        try {
            val raw: Bitmap = imageProxy.toBitmap()

            // Rotate to upright orientation — sensor delivers frames rotated relative to display
            val rotation = imageProxy.imageInfo.rotationDegrees
            val bitmap = if (rotation != 0) {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                    .also { raw.recycle() }
            } else raw

            // Two-stage pipeline: EyeDetector (ML Kit) → EyeGazeModel (NPU/CPU)
            val gazeResult = gazeEstimator.estimate(bitmap, eyeGazeModel)

            bitmap.recycle()

            viewModel.onGazeUpdate(
                gazeResult = gazeResult,
                inferenceMs = eyeGazeModel.lastInferenceMs,
                accelerator = eyeGazeModel.acceleratorName
            )

        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error: ${e.message}", e)
        } finally {
            imageProxy.close()  // always — never skip this
        }
    }

    fun stop() {
        inferenceExecutor.shutdown()
        Log.i(TAG, "Camera pipeline stopped")
    }
}
