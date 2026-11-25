package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AnatomyCatalog
import com.example.data.CalibrationRepository
import com.example.data.GlbImportResult
import com.example.data.GlbModelRepository
import com.example.data.InstalledGlbModel
import com.example.model.AnatomyPart
import com.example.model.AnatomySystem
import com.example.model.BodyCalibration
import com.example.model.BodyRegion
import com.example.model.BodyViewDirection
import com.example.model.CalibrationSession
import com.example.model.Pt
import com.example.model.StageAspect
import com.example.model.TorsoFrame
import com.example.model.TrackingFrame
import com.example.model.ViewMode
import com.example.model.VisiblePart
import com.example.model.VisualLook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

data class XRayUiState(
    val started: Boolean = false,
    val cameraPermissionDenied: Boolean = false,
    val cameraError: String? = null,
    val isFrontCamera: Boolean = true,
    val system: AnatomySystem = AnatomySystem.ALL,
    val look: VisualLook = VisualLook.XRAY,
    val viewMode: ViewMode = ViewMode.FULL,
    val selectedRegion: BodyRegion = BodyRegion.ALL,
    val bodyVisibility: Float = 0.25f,
    val lensRadiusFrac: Float = 0.18f,
    val showLabels: Boolean = true,
    val viewDirection: BodyViewDirection = BodyViewDirection.AUTO,
    val segEnabled: Boolean = false,
    val stageAspect: StageAspect = StageAspect.FULLSCREEN,
    val flipYaw: Boolean = false,
    val debugMode: Boolean = false,
    val sheetExpanded: Boolean = true,
    val tourActive: Boolean = false,
    val searchQuery: String = "",
    val selectedPart: AnatomyPart? = null,
    val highlightUntilMs: Long = 0L,
    val cardMessage: String? = null,
    val cardUntilMs: Long = 0L,
    val calibration: BodyCalibration? = null,
    val calibrationSession: CalibrationSession = CalibrationSession(),
    val recordingCountdown: Boolean = false,
    val recordingCountdownEndMs: Long = 0L,
    val isRecording: Boolean = false,
    val recordingStartMs: Long = 0L,
    val recordingSeconds: Int = 0,
    val fps: Int = 0,
    val activeTrackers: List<String> = emptyList(),
    val detectedZoneNames: List<String> = emptyList(),
    val isBackView: Boolean = false,
    val visiblePartsCount: Int = 0,
    val totalPartsCount: Int = AnatomyCatalog.parts.size,
    val showFilament3d: Boolean = false,
    val liveBodyYawDeg: Float = 0f,
    val installedGlbModels: List<InstalledGlbModel> = emptyList(),
    val selectedGlbId: String? = null,
    val showAttributions: Boolean = false,
    val glbImporting: Boolean = false,
)

class XRayViewModel(application: Application) : AndroidViewModel(application) {

    private val calibrationRepo = CalibrationRepository(application)

    private val _uiState = MutableStateFlow(XRayUiState())
    val uiState: StateFlow<XRayUiState> = _uiState.asStateFlow()

    private val _trackingFrame = MutableStateFlow(TrackingFrame())
    val trackingFrame: StateFlow<TrackingFrame> = _trackingFrame.asStateFlow()

    @Volatile
    var latestVisibleParts: List<VisiblePart> = emptyList()
        private set

    private var tourIndex = 0
    private var nextTourStepMs = 0L

    init {
        viewModelScope.launch {
            calibrationRepo.calibrationFlow.collect { savedCal ->
                _uiState.update { it.copy(calibration = savedCal) }
            }
        }
        refreshInstalledGlbModels()
    }

    fun refreshInstalledGlbModels() {
        viewModelScope.launch {
            val models = GlbModelRepository.listInstalledModels(getApplication())
            _uiState.update { state ->
                val keepSelected = state.selectedGlbId?.takeIf { id -> models.any { it.id == id } }
                state.copy(
                    installedGlbModels = models,
                    selectedGlbId = keepSelected,
                )
            }
        }
    }

    fun importGlbUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(glbImporting = true) }
            when (val res = GlbModelRepository.importGlbFromUri(getApplication(), uri)) {
                is GlbImportResult.Success -> {
                    val models = GlbModelRepository.listInstalledModels(getApplication())
                    _uiState.update {
                        it.copy(
                            glbImporting = false,
                            installedGlbModels = models,
                            selectedGlbId = res.model.id,
                            showFilament3d = true,
                            showAttributions = false,
                        )
                    }
                    showToast("تم حفظ النموذج محليًا للعمل دون إنترنت: ${res.model.displayName}")
                }
                is GlbImportResult.Error -> {
                    _uiState.update { it.copy(glbImporting = false) }
                    showToast(res.message)
                }
            }
        }
    }

    fun selectGlbModel(id: String?) {
        _uiState.update { it.copy(selectedGlbId = id) }
    }

    fun deleteGlbModel(model: InstalledGlbModel) {
        viewModelScope.launch {
            if (GlbModelRepository.deleteImportedModel(model)) {
                val models = GlbModelRepository.listInstalledModels(getApplication())
                _uiState.update { state ->
                    state.copy(
                        installedGlbModels = models,
                        selectedGlbId = if (state.selectedGlbId == model.id) null else state.selectedGlbId,
                    )
                }
                showToast("تم حذف النموذج المحلي: ${model.displayName}")
            }
        }
    }

    fun setShowAttributions(show: Boolean) {
        _uiState.update { it.copy(showAttributions = show) }
    }

    fun onStartClicked() {
        _uiState.update { it.copy(started = true, cameraPermissionDenied = false, cameraError = null) }
    }

    fun onPermissionDenied() {
        _uiState.update { it.copy(cameraPermissionDenied = true) }
    }

    fun onCameraError(error: String) {
        _uiState.update { it.copy(cameraError = error) }
    }

    fun toggleCamera() {
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera) }
    }

    fun setSystem(sys: AnatomySystem) = _uiState.update { it.copy(system = sys) }
    fun setLook(look: VisualLook) = _uiState.update { it.copy(look = look) }
    fun setViewMode(mode: ViewMode) = _uiState.update { it.copy(viewMode = mode) }
    fun setRegion(region: BodyRegion) = _uiState.update { it.copy(selectedRegion = region) }
    fun setBodyVisibility(value: Float) = _uiState.update { it.copy(bodyVisibility = value.coerceIn(0f, 1f)) }
    fun setLensRadius(value: Float) = _uiState.update { it.copy(lensRadiusFrac = value.coerceIn(0.08f, 0.40f)) }
    fun toggleLabels() = _uiState.update { it.copy(showLabels = !it.showLabels) }
    fun cycleViewDirection() = _uiState.update { state ->
        val next = when (state.viewDirection) {
            BodyViewDirection.AUTO -> BodyViewDirection.FRONT
            BodyViewDirection.FRONT -> BodyViewDirection.BACK
            BodyViewDirection.BACK -> BodyViewDirection.AUTO
        }
        state.copy(viewDirection = next)
    }

    fun toggleSegmentation() {
        val next = !_uiState.value.segEnabled
        _uiState.update { it.copy(segEnabled = next) }
        if (next) {
            showToast("قص الأعضاء داخل الجسم: مفعّل محليًا على الجهاز.")
        }
    }

    fun cycleStageAspect() = _uiState.update { state ->
        val next = when (state.stageAspect) {
            StageAspect.FULLSCREEN -> StageAspect.RATIO_16_9
            StageAspect.RATIO_16_9 -> StageAspect.RATIO_9_16
            StageAspect.RATIO_9_16 -> StageAspect.FULLSCREEN
        }
        state.copy(stageAspect = next)
    }

    fun toggleFlipYaw() = _uiState.update { it.copy(flipYaw = !it.flipYaw) }
    fun toggleDebugMode() = _uiState.update { it.copy(debugMode = !it.debugMode) }
    fun toggleSheetExpanded() = _uiState.update { it.copy(sheetExpanded = !it.sheetExpanded) }
    fun toggleFilament3d() = _uiState.update { it.copy(showFilament3d = !it.showFilament3d) }

    fun toggleTour() {
        val next = !_uiState.value.tourActive
        nextTourStepMs = 0L
        _uiState.update { it.copy(tourActive = next) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun executeSearch(queryInput: String = _uiState.value.searchQuery) {
        val q = AnatomyCatalog.normalizeArabic(queryInput)
        if (q.isEmpty()) return
        val matches = AnatomyCatalog.parts.filter { AnatomyCatalog.normalizeArabic(it.ar).contains(q) }
        if (matches.isEmpty()) {
            showToast("لا يوجد جزء بهذا الاسم في القائمة.")
            return
        }
        val visibleSet = latestVisibleParts.map { it.part }.toSet()
        val visibleMatch = matches.firstOrNull { it in visibleSet }
        if (visibleMatch != null) {
            selectPart(visibleMatch, 6000L)
        } else {
            showToast("${matches.first().ar}: غير ظاهر الآن. ابتعد قليلًا أو غيّر وضعك حتى يدخل هذا الجزء في الكاميرا.")
        }
    }

    fun selectPart(part: AnatomyPart, durationMs: Long = 4500L) {
        val until = System.currentTimeMillis() + durationMs
        _uiState.update {
            it.copy(
                selectedPart = part,
                highlightUntilMs = until,
                cardMessage = null,
                cardUntilMs = until,
            )
        }
    }

    fun showToast(message: String, durationMs: Long = 4500L) {
        val until = System.currentTimeMillis() + durationMs
        _uiState.update {
            it.copy(
                selectedPart = null,
                cardMessage = message,
                cardUntilMs = until,
            )
        }
    }

    fun dismissCard() {
        _uiState.update { it.copy(selectedPart = null, cardMessage = null, cardUntilMs = 0L) }
    }

    fun startCalibration() {
        val frame = _trackingFrame.value
        if (!frame.hasPose || frame.poseNorm.size < 25) {
            showToast("قف أمام الكاميرا ليظهر جسمك (الكتفان والوركان على الأقل) ثم أعد المحاولة.")
            return
        }
        val now = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                calibrationSession = CalibrationSession(
                    stage = 1,
                    targetTimeMs = now + 3000L,
                )
            )
        }
    }

    fun onTrackingFrame(frame: TrackingFrame) {
        _trackingFrame.value = frame
        val trackers = buildList {
            if (frame.hasFace) add("وجه")
            if (frame.hasHands) add("يدان")
            if (frame.hasPose) add("جسم")
        }
        _uiState.update { it.copy(fps = frame.fps, activeTrackers = trackers) }
    }

    fun onFrameRendered(
        nowMs: Long,
        visibleParts: List<VisiblePart>,
        totalActive: Int,
        detectedZoneNames: List<String>,
        isBackView: Boolean,
        torso: TorsoFrame?,
        poseScreen: List<Pt>,
    ) {
        latestVisibleParts = visibleParts
        val cur = _uiState.value

        // Guided tour tick
        if (cur.tourActive && nowMs >= nextTourStepMs && visibleParts.isNotEmpty()) {
            val part = visibleParts[tourIndex++ % visibleParts.size].part
            nextTourStepMs = nowMs + 3000L
            selectPart(part, 3000L)
        }

        // Auto-hide card after timeout
        if ((cur.selectedPart != null || cur.cardMessage != null) && nowMs > cur.cardUntilMs && cur.cardUntilMs > 0L) {
            dismissCard()
        }

        // Calibration state machine
        val cs = cur.calibrationSession
        if (cs.stage == 1 && nowMs >= cs.targetTimeMs) {
            _uiState.update {
                it.copy(
                    calibrationSession = cs.copy(stage = 2, endTimeMs = nowMs + 1300L)
                )
            }
        } else if (cs.stage == 2) {
            if (torso != null && abs(torso.cosYaw) >= 0.88f && !isBackView) {
                val len = torso.length
                cs.rW.add(hypot(torso.a.x - torso.b.x, torso.a.y - torso.b.y) / (abs(torso.cosYaw) * len))
                fun sample(a: Int, b: Int, list: MutableList<Float>) {
                    val pa = poseScreen.getOrNull(a)?.takeIf { it.v >= 0.45f } ?: return
                    val pb = poseScreen.getOrNull(b)?.takeIf { it.v >= 0.45f } ?: return
                    list.add(hypot(pa.x - pb.x, pa.y - pb.y) / len)
                }
                sample(12, 14, cs.rA)
                sample(11, 13, cs.rA)
                sample(14, 16, cs.rF)
                sample(13, 15, cs.rF)
                sample(24, 26, cs.rT)
                sample(23, 25, cs.rT)
                sample(26, 28, cs.rS)
                sample(25, 27, cs.rS)
            }
            if (nowMs >= cs.endTimeMs) {
                finishCalibration(cs)
            }
        }

        val bodyYaw = if (torso != null) {
            (kotlin.math.atan2(torso.sinYaw, torso.cosYaw) * 180f / kotlin.math.PI.toFloat())
        } else {
            cur.liveBodyYawDeg
        }
        if (
            cur.visiblePartsCount != visibleParts.size ||
            cur.totalPartsCount != totalActive ||
            cur.detectedZoneNames != detectedZoneNames ||
            cur.isBackView != isBackView ||
            kotlin.math.abs(cur.liveBodyYawDeg - bodyYaw) > 1.5f
        ) {
            _uiState.update {
                it.copy(
                    visiblePartsCount = visibleParts.size,
                    totalPartsCount = totalActive,
                    detectedZoneNames = detectedZoneNames,
                    isBackView = isBackView,
                    liveBodyYawDeg = bodyYaw,
                )
            }
        }
    }

    private fun finishCalibration(cs: CalibrationSession) {
        _uiState.update { it.copy(calibrationSession = CalibrationSession()) }
        if (cs.rW.size < 8) {
            showToast("لم أتمكن من القياس. قف مواجهًا الكاميرا وجسمك ظاهر ثم أعد المحاولة.")
            return
        }
        fun median(list: List<Float>): Float {
            if (list.isEmpty()) return 0f
            val sorted = list.sorted()
            return sorted[sorted.size / 2]
        }
        val cal = BodyCalibration(
            rW = median(cs.rW),
            rA = if (cs.rA.size >= 6) median(cs.rA) else 0f,
            rF = if (cs.rF.size >= 6) median(cs.rF) else 0f,
            rT = if (cs.rT.size >= 6) median(cs.rT) else 0f,
            rS = if (cs.rS.size >= 6) median(cs.rS) else 0f,
        )
        viewModelScope.launch {
            calibrationRepo.saveCalibration(cal)
        }
        val suffix = if (cal.hasLimbs) " (الجذع والذراعان والساقان)." else " (الجذع فقط؛ لم تظهر الأطراف كاملة)."
        showToast("تمت معايرة جسمك وحفظها محليًا$suffix")
    }

    fun setRecordingCountdown(active: Boolean, endMs: Long = 0L) {
        _uiState.update { it.copy(recordingCountdown = active, recordingCountdownEndMs = endMs) }
    }

    fun setRecordingState(recording: Boolean, startMs: Long = 0L) {
        _uiState.update { it.copy(isRecording = recording, recordingStartMs = startMs, recordingSeconds = 0) }
    }

    fun updateRecordingSeconds(sec: Int) {
        if (_uiState.value.recordingSeconds != sec) {
            _uiState.update { it.copy(recordingSeconds = sec) }
        }
    }
}
