package com.gazeboard.state

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.gazeboard.audio.TtsManager
import com.gazeboard.calibration.CalibrationEngine
import com.gazeboard.camera.CameraManager
import com.gazeboard.ml.EyeGazeModel
import com.gazeboard.ml.GazeEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GazeBoardViewModel(application: Application) : AndroidViewModel(application) {

    private val _appState = MutableStateFlow<AppState>(AppState.Initializing)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _gazePoint = MutableStateFlow<Pair<Float, Float>?>(null)
    val gazePoint: StateFlow<Pair<Float, Float>?> = _gazePoint.asStateFlow()

    private val _dwellingCellIndex = MutableStateFlow(-1)
    val dwellingCellIndex: StateFlow<Int> = _dwellingCellIndex.asStateFlow()

    private val _dwellProgress = MutableStateFlow(0f)
    val dwellProgress: StateFlow<Float> = _dwellProgress.asStateFlow()

    private val _lastSpokenPhrase = MutableStateFlow<String?>(null)
    val lastSpokenPhrase: StateFlow<String?> = _lastSpokenPhrase.asStateFlow()

    private val _inferenceMs = MutableStateFlow(0L)
    val inferenceMs: StateFlow<Long> = _inferenceMs.asStateFlow()

    private val _acceleratorName = MutableStateFlow("—")
    val acceleratorName: StateFlow<String> = _acceleratorName.asStateFlow()

    // Reactive calibration target index — fixes non-reactive direct property read bug
    private val _calibTargetIndex = MutableStateFlow(0)
    val calibTargetIndex: StateFlow<Int> = _calibTargetIndex.asStateFlow()

    // Face detection state for overlay and "no face" warning
    private val _faceDetected = MutableStateFlow(false)
    val faceDetected: StateFlow<Boolean> = _faceDetected.asStateFlow()

    // Normalized eye center [0,1] for camera PiP overlay dot
    private val _eyeCenterNorm = MutableStateFlow<Pair<Float, Float>?>(null)
    val eyeCenterNorm: StateFlow<Pair<Float, Float>?> = _eyeCenterNorm.asStateFlow()

    // ML Kit face detection latency for pipeline stats display
    private val _faceDetectMs = MutableStateFlow(0L)
    val faceDetectMs: StateFlow<Long> = _faceDetectMs.asStateFlow()

    // Exposed to composables so PreviewView can call setSurfaceProvider().
    // Created here (not in CameraManager) so it's always available before camera starts.
    val cameraPreview: Preview = Preview.Builder().build()

    val eyeGazeModel = EyeGazeModel(application)
    val gazeEstimator = GazeEstimator()
    val calibEngine = CalibrationEngine()
    val ttsManager = TtsManager(application)

    private var cameraManager: CameraManager? = null

    val phrases = listOf("Yes", "No", "Help", "Thank you", "I need water", "I'm in pain")

    private val dwellDurationMs = 1500L
    private val calibrationDwellMs = 2000L

    private var dwellStartMs = 0L
    private var currentDwellCell = -1
    private var calibDwellStartMs = 0L
    private var screenW = 1080f
    private var screenH = 2400f

    private val calibrationPrompts = listOf(
        "Look at the top left corner",
        "Look at the top right corner",
        "Look at the bottom left corner",
        "Look at the bottom right corner"
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                eyeGazeModel.load()
                _acceleratorName.value = eyeGazeModel.acceleratorName
                ttsManager.init()
                _appState.value = AppState.NeedsPermission
                Log.i(TAG, "Model loaded on ${eyeGazeModel.acceleratorName}")
            } catch (e: Exception) {
                _appState.value = AppState.Error("Model load failed: ${e.message}")
                Log.e(TAG, "Model load failed", e)
            }
        }
    }

    fun setScreenSize(w: Float, h: Float) {
        screenW = w
        screenH = h
        calibEngine.setScreenSize(w, h)
    }

    /**
     * Called once after camera permission is granted. Creates CameraManager if not yet created.
     * Always restarts calibration so the user calibrates on every fresh camera start.
     */
    fun onCameraPermissionGranted(lifecycleOwner: LifecycleOwner) {
        startCalibration()
        if (cameraManager == null) {
            cameraManager = CameraManager(
                getApplication(),
                cameraPreview,
                eyeGazeModel,
                gazeEstimator,
                this
            ).also { it.start(lifecycleOwner) }
        }
    }

    /**
     * Called from MainActivity.onResume(). Forces recalibration every time the app
     * comes to foreground so gaze accuracy is always fresh.
     * CameraX lifecycle handles camera resume automatically — no explicit rebind needed.
     */
    fun onActivityResumed(lifecycleOwner: LifecycleOwner) {
        when (_appState.value) {
            AppState.Board, AppState.Calibrating -> startCalibration()
            else -> Unit  // Initializing / NeedsPermission / Error — don't interrupt
        }
    }

    fun startCalibration() {
        val margin = 80f
        calibDwellStartMs = 0L
        calibEngine.reset()
        gazeEstimator.reset()
        calibEngine.setScreenSize(screenW, screenH)
        calibEngine.setCalibrationTargets(
            listOf(
                CalibrationEngine.CalibPoint(margin, margin),
                CalibrationEngine.CalibPoint(screenW - margin, margin),
                CalibrationEngine.CalibPoint(margin, screenH - margin),
                CalibrationEngine.CalibPoint(screenW - margin, screenH - margin)
            )
        )
        _calibTargetIndex.value = 0
        _appState.value = AppState.Calibrating
        _gazePoint.value = null
        _eyeCenterNorm.value = null
        resetDwell()

        // Brief delay so the calibration screen renders before TTS fires
        viewModelScope.launch {
            delay(400L)
            ttsManager.speak(calibrationPrompts[0])
        }
    }

    fun onGazeUpdate(
        gazeResult: GazeEstimator.GazeResult?,
        inferenceMs: Long,
        accelerator: String
    ) {
        _inferenceMs.value = inferenceMs
        _acceleratorName.value = accelerator
        _faceDetected.value = gazeResult != null

        if (gazeResult == null) {
            _gazePoint.value = null
            _eyeCenterNorm.value = null
            resetDwell()
            return
        }

        _eyeCenterNorm.value = Pair(gazeResult.eyeCenterNormX, gazeResult.eyeCenterNormY)
        _faceDetectMs.value = gazeResult.faceDetectMs

        when (_appState.value) {
            AppState.Calibrating -> handleCalibrationFrame(gazeResult)
            AppState.Board -> handleBoardFrame(gazeResult)
            else -> Unit
        }
    }

    private fun handleCalibrationFrame(gaze: GazeEstimator.GazeResult) {
        calibEngine.accumulateSample(gaze.pitch, gaze.yaw)

        if (calibDwellStartMs == 0L) {
            calibDwellStartMs = SystemClock.elapsedRealtime()
        }

        val elapsed = SystemClock.elapsedRealtime() - calibDwellStartMs
        _dwellProgress.value = (elapsed / calibrationDwellMs.toFloat()).coerceIn(0f, 1f)

        if (elapsed >= calibrationDwellMs) {
            calibDwellStartMs = 0L
            _dwellProgress.value = 0f

            val done = calibEngine.commitCurrentTarget()
            _calibTargetIndex.value = calibEngine.currentTargetIndex

            if (done) {
                _appState.value = AppState.Board
                viewModelScope.launch {
                    ttsManager.speak("Calibration complete")
                }
            } else {
                // Speak the next corner prompt
                calibrationPrompts.getOrNull(calibEngine.currentTargetIndex)?.let { prompt ->
                    viewModelScope.launch { ttsManager.speak(prompt) }
                }
            }
        }
    }

    private fun handleBoardFrame(gaze: GazeEstimator.GazeResult) {
        val screenPt = calibEngine.toScreenPoint(gaze.pitch, gaze.yaw) ?: return
        _gazePoint.value = screenPt

        val gazedCell = hitTestCell(screenPt.first, screenPt.second)
        if (gazedCell != currentDwellCell) {
            currentDwellCell = gazedCell
            dwellStartMs = SystemClock.elapsedRealtime()
            _dwellingCellIndex.value = gazedCell
            _dwellProgress.value = 0f
            return
        }

        if (gazedCell < 0) return

        val elapsed = SystemClock.elapsedRealtime() - dwellStartMs
        _dwellProgress.value = (elapsed / dwellDurationMs.toFloat()).coerceIn(0f, 1f)

        if (elapsed >= dwellDurationMs) {
            val phrase = phrases.getOrNull(gazedCell) ?: return
            ttsManager.speak(phrase)
            _lastSpokenPhrase.value = phrase
            viewModelScope.launch {
                delay(2000L)
                if (_lastSpokenPhrase.value == phrase) _lastSpokenPhrase.value = null
            }
            resetDwell()
        }
    }

    private fun hitTestCell(sx: Float, sy: Float): Int {
        val col = (sx / (screenW / 3f)).toInt().coerceIn(0, 2)
        val row = (sy / (screenH / 2f)).toInt().coerceIn(0, 1)
        return row * 3 + col
    }

    private fun resetDwell() {
        currentDwellCell = -1
        dwellStartMs = 0L
        _dwellingCellIndex.value = -1
        _dwellProgress.value = 0f
    }

    override fun onCleared() {
        cameraManager?.stop()
        eyeGazeModel.close()
        gazeEstimator.close()
        ttsManager.shutdown()
        super.onCleared()
    }

    companion object {
        private const val TAG = "GazeBoard"
    }
}
