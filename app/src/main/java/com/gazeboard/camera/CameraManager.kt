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
import com.gazeboard.calibration.CalibrationEngine
import com.gazeboard.ml.EyeGazeModel
import com.gazeboard.ml.GazeEstimator
import com.gazeboard.state.GazeBoardViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX ImageAnalysis pipeline.
 *
 * Frame pipeline:
 *   ImageProxy (RGBA_8888) → toBitmap() → rotate to upright
 *     → GazeEstimator.estimate() → GazeResult(quadrant)
 *     → GazeBoardViewModel.onGazeUpdate()
 *
 * Uses STRATEGY_KEEP_ONLY_LATEST to drop stale frames and bound pipeline latency.
 * Never blocks the main thread — all inference runs on inferenceExecutor.
 */
class CameraManager(
    private val context: Context,
    private val eyeGazeModel: EyeGazeModel,
    private val gazeEstimator: GazeEstimator,
    private val calibrationEngine: CalibrationEngine,
    private val viewModel: GazeBoardViewModel
) {
    private val inferenceExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    val preview: Preview = Preview.Builder().build()

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
                Log.i(TAG, "CameraX bound (640×480, RGBA_8888, KEEP_ONLY_LATEST)")
            } catch (e: Exception) {
                Log.e(TAG, "CameraX bind failed: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            val raw: Bitmap = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees
            val bitmap = if (rotation != 0) {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                    .also { raw.recycle() }
            } else raw

            val result = gazeEstimator.estimate(bitmap, eyeGazeModel, calibrationEngine)
            bitmap.recycle()
            viewModel.onGazeUpdate(result)
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error: ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }

    fun stop() {
        inferenceExecutor.shutdown()
        Log.i(TAG, "Camera pipeline stopped")
    }
}
