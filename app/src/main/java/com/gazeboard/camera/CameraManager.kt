package com.gazeboard.camera

import android.content.Context
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.gazeboard.ml.FaceLandmarkModel
import com.gazeboard.ml.GazeEstimator
import com.gazeboard.state.GazeBoardViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages the CameraX ImageAnalysis pipeline.
 *
 * PERSON B OWNS THIS FILE.
 *
 * Responsibilities:
 *  - Bind front camera to lifecycle
 *  - Deliver frames to FaceLandmarkModel on a background thread
 *  - Forward GazeEstimator results to GazeBoardViewModel
 *  - Never block the main thread; use STRATEGY_KEEP_ONLY_LATEST to drop stale frames
 *
 * Frame pipeline:
 *   ImageProxy (RGBA_8888) → toBitmap() → FaceLandmarkModel.runInference()
 *   → GazeEstimator.estimate() → GazeBoardViewModel.onGazeUpdate()
 *
 * TODO(Person B): Call start() from GazeBoardViewModel.onCameraPermissionGranted().
 */
class CameraManager(
    private val context: Context,
    private val faceLandmarkModel: FaceLandmarkModel,
    private val gazeEstimator: GazeEstimator,
    private val viewModel: GazeBoardViewModel
) {

    // Single-threaded executor for inference; keeps frames in order
    private val inferenceExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    companion object {
        private const val TAG = "GazeBoard"
    }

    /**
     * Bind CameraX analysis use case to the given lifecycle owner.
     * Safe to call from the main thread.
     *
     * TODO(Person B): Implement body below.
     */
    fun start(lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setTargetRotation(Surface.ROTATION_0)
                // STRATEGY_KEEP_ONLY_LATEST: drop frames when inference can't keep up.
                // This prevents memory growth and keeps latency bounded.
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
                    imageAnalysis
                )
                Log.i(TAG, "CameraX ImageAnalysis bound to lifecycle")
            } catch (e: Exception) {
                Log.e(TAG, "CameraX bind failed: ${e.message}", e)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Process a single frame from CameraX.
     *
     * MUST call imageProxy.close() before returning — failure stalls the pipeline.
     */
    private fun processFrame(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmap()

            val landmarks = faceLandmarkModel.runInference(bitmap)

            if (landmarks != null) {
                val result = gazeEstimator.estimate(landmarks)
                viewModel.onGazeUpdate(
                    rawGaze = result?.gaze,
                    inferenceMs = faceLandmarkModel.lastInferenceMs,
                    accelerator = faceLandmarkModel.acceleratorName,
                    isBlinking = result?.isBlinking ?: false,
                    distanceWarning = result?.distanceWarning
                )
            } else {
                // No face detected — notify ViewModel so UI can show warning overlay
                viewModel.onGazeUpdate(
                    rawGaze = null,
                    inferenceMs = faceLandmarkModel.lastInferenceMs,
                    accelerator = faceLandmarkModel.acceleratorName,
                    isBlinking = false,
                    distanceWarning = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error: ${e.message}", e)
        } finally {
            // Always close — this releases the buffer back to the camera pipeline
            imageProxy.close()
        }
    }

    fun stop() {
        inferenceExecutor.shutdown()
        Log.i(TAG, "Camera pipeline stopped")
    }
}
