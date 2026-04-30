package com.gazeboard.state

import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gazeboard.audio.TtsManager
import com.gazeboard.calibration.CalibrationEngine
import com.gazeboard.camera.CameraManager
import com.gazeboard.ml.EyeDetector
import com.gazeboard.ml.EyeGazeModel
import com.gazeboard.ml.GazeEstimator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Published to UI on every frame. All fields safe to read on the main thread.
 *
 * gazePoint is in screen pixels; null when no face is detected.
 * pitch/yaw are in the model's raw radian space (logged/debug only — not shown in UI).
 */
data class GazeState(
    val gazePoint: Offset? = null,       // calibrated position in screen pixels
    val hoveredCell: Int? = null,        // 0–5 (row-major 2×3 grid)
    val dwellProgress: Float = 0f,       // 0.0–1.0
    val inferenceMs: Long = 0L,
    val accelerator: String = "—",
    val faceDetected: Boolean = false,
    val rawPitch: Float = 0f,            // for debug overlay / calibration
    val rawYaw: Float = 0f
)

class GazeBoardViewModel : ViewModel() {

    private val _appState = MutableStateFlow<AppState>(AppState.Calibrating)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _gazeState = MutableStateFlow(GazeState())
    val gazeState: StateFlow<GazeState> = _gazeState.asStateFlow()

    val phrases = listOf("Yes", "No", "Help", "Thank you", "I need water", "Call nurse")

    private val DWELL_THRESHOLD_MS = 1500L
    private val COOLDOWN_MS = 500L

    private var currentDwellCell: Int? = null
    private var dwellStartMs: Long = 0L

    // Screen dimensions needed to convert calibrated screen coords → Offset
    // Set by BoardScreen via onGloballyPositioned
    var screenWidth: Float = 1080f
    var screenHeight: Float = 2400f

    private lateinit var eyeGazeModel: EyeGazeModel
    private lateinit var gazeEstimator: GazeEstimator
    private lateinit var calibrationEngine: CalibrationEngine
    private lateinit var cameraManager: CameraManager
    private lateinit var ttsManager: TtsManager

    fun onCameraPermissionGranted(context: Context) {
        viewModelScope.launch {
            // TODO(Person A): Initialize and load the EyeGaze model on a background thread
            // eyeGazeModel = EyeGazeModel(context)
            // eyeGazeModel.load()   // ~2-5s first launch for JIT NPU compilation

            // TODO(Person B): Initialize gaze pipeline
            // gazeEstimator = GazeEstimator()
            // calibrationEngine = CalibrationEngine()
            // cameraManager = CameraManager(context, eyeGazeModel, gazeEstimator, this@GazeBoardViewModel)
            // cameraManager.start(lifecycleOwner)  // need LifecycleOwner from Activity

            // TODO(Person C): Get TtsManager from Application singleton
            // ttsManager = (context.applicationContext as GazeBoardApplication).ttsManager

            Log.i(TAG, "Camera permission granted — pipeline initialization pending")
        }
    }

    /**
     * Called by CameraManager each frame.
     *
     * @param gazeResult null when no face detected; contains pitch/yaw when face is visible
     * @param inferenceMs NPU inference time for the EyeGaze model
     * @param accelerator "NPU", "GPU", or "CPU"
     */
    fun onGazeUpdate(
        gazeResult: GazeEstimator.GazeResult?,
        inferenceMs: Long,
        accelerator: String
    ) {
        if (gazeResult == null) {
            _gazeState.update {
                it.copy(
                    faceDetected = false,
                    gazePoint = null,
                    hoveredCell = null,
                    dwellProgress = 0f,
                    inferenceMs = inferenceMs,
                    accelerator = accelerator
                )
            }
            resetDwell()
            return
        }

        // TODO(Person B): wire calibrationEngine when initialized
        // val screenPos = calibrationEngine.applyCalibration(gazeResult.pitch, gazeResult.yaw)
        // Stub: map pitch/yaw directly to screen fraction for integration testing
        val screenX = ((-gazeResult.yaw / 0.8f + 1f) / 2f * screenWidth).coerceIn(0f, screenWidth)
        val screenY = ((gazeResult.pitch / 0.5f + 1f) / 2f * screenHeight).coerceIn(0f, screenHeight)
        val screenPos = PointF(screenX, screenY)

        val gazeOffset = Offset(screenPos.x, screenPos.y)

        // Map screen position to 2×3 grid cell
        val cellIndex = mapToCell(screenPos)

        _gazeState.update {
            it.copy(
                gazePoint = gazeOffset,
                hoveredCell = cellIndex,
                inferenceMs = inferenceMs,
                accelerator = accelerator,
                faceDetected = true,
                rawPitch = gazeResult.pitch,
                rawYaw = gazeResult.yaw
            )
        }

        if (_appState.value == AppState.Tracking) {
            onCellHovered(cellIndex)
        }
    }

    fun onCalibrationPointCaptured(screenPoint: PointF, pitchYaw: PointF) {
        // TODO(Person B): calibrationEngine.addCalibrationPoint(screenPoint, pitchYaw)
    }

    fun onCalibrationComplete() {
        // TODO(Person B): calibrationEngine.computeAffineTransform()
        _appState.value = AppState.Tracking
        Log.i(TAG, "Calibration complete — entering tracking mode")
    }

    fun startCalibration() {
        // TODO(Person B): calibrationEngine.reset()
        _appState.value = AppState.Calibrating
    }

    private fun onCellHovered(cellIndex: Int?) {
        when {
            cellIndex == null -> resetDwell()
            cellIndex != currentDwellCell -> {
                currentDwellCell = cellIndex
                dwellStartMs = SystemClock.elapsedRealtime()
                _gazeState.update { it.copy(dwellProgress = 0f) }
            }
            else -> {
                val elapsed = SystemClock.elapsedRealtime() - dwellStartMs
                val progress = (elapsed.toFloat() / DWELL_THRESHOLD_MS).coerceIn(0f, 1f)
                _gazeState.update { it.copy(dwellProgress = progress) }
                if (elapsed >= DWELL_THRESHOLD_MS) selectCell(cellIndex)
            }
        }
    }

    private fun selectCell(index: Int) {
        val phrase = phrases.getOrNull(index) ?: return
        Log.i(TAG, "Cell $index selected: \"$phrase\"")
        // TODO(Person C): ttsManager.speak(phrase)
        _appState.value = AppState.Selected(index)
        resetDwell()
        viewModelScope.launch {
            delay(COOLDOWN_MS)
            if (_appState.value is AppState.Selected) _appState.value = AppState.Tracking
        }
    }

    private fun resetDwell() {
        currentDwellCell = null
        dwellStartMs = 0L
        _gazeState.update { it.copy(dwellProgress = 0f, hoveredCell = null) }
    }

    /**
     * Map a screen pixel position to a 2×3 grid cell index (row-major).
     *   0 | 1 | 2
     *   --|---|--
     *   3 | 4 | 5
     */
    private fun mapToCell(screenPos: PointF): Int? {
        val col = (screenPos.x / screenWidth * 3).toInt().coerceIn(0, 2)
        val row = (screenPos.y / screenHeight * 2).toInt().coerceIn(0, 1)
        return row * 3 + col
    }

    override fun onCleared() {
        // TODO: cameraManager.stop()
        // TODO: eyeGazeModel.close()
        super.onCleared()
    }

    companion object {
        private const val TAG = "GazeBoard"
    }
}
