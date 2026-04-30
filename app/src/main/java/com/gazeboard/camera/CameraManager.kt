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
 * Frame pipeline:
 *   ImageProxy (ARGB_8888) → toBitmap() → GazeEstimator.estimate()
 *     → EyeDetector [ML Kit, CPU] → eye crop FloatBuffer
 *     → EyeGazeModel [CompiledModel, NPU] → pitch, yaw
 *   → GazeBoardViewModel.onGazeUpdate()
 *
 * The [preview] use case is created in GazeBoardViewModel and passed in here so
 * composables can call preview.setSurfaceProvider() before the camera starts.
 * Uses STRATEGY_KEEP_ONLY_LATEST to drop stale frames and keep pipeline latency bounded.
 */
class CameraManager(
    private val context: Context,
    private val preview: Preview,
    private val eyeGazeModel: EyeGazeModel,
    private val gazeEstimator: GazeEstimator,
    private val viewModel: GazeBoardViewModel
) {

    private val inferenceExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    companion object {
        private const val TAG = "GazeBoard"
    }

    fun start(lifecycleOwner: LifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({
            val cameraProvider = providerFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setTargetRotation(Surface.ROTATION_0)
                // Discard queued frames so we always process the freshest.
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
                Log.i(TAG, "CameraX bound (640×480, RGBA_8888, KEEP_ONLY_LATEST, front camera)")
            } catch (e: Exception) {
                Log.e(TAG, "CameraX bind failed: ${e.message}", e)
            }

        }, ContextCompat.getMainExecutor(context))
    }

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

            val gazeResult = gazeEstimator.estimate(bitmap, eyeGazeModel)

            bitmap.recycle()

            viewModel.onGazeUpdate(
                gazeResult   = gazeResult,
                inferenceMs  = eyeGazeModel.lastInferenceMs,
                accelerator  = eyeGazeModel.acceleratorName
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
