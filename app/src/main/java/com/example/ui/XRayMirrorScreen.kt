package com.example.ui

import android.app.Activity
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.Pt
import com.example.model.StageAspect
import com.example.render.AnatomyOverlayRenderer
import com.example.render.ExportResult
import com.example.render.FilamentAnatomyOverlay
import com.example.render.MediaExporter
import com.example.render.OverlayRenderSettings
import com.example.render.OverlayVideoRecorder
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.GoldHighlight
import com.example.vision.AnatomyTrackerEngine
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.max

@Composable
fun XRayMirrorScreen(
    viewModel: XRayViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val trackingFrame by viewModel.trackingFrame.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()

    val arabicTypeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.tajawal) ?: android.graphics.Typeface.DEFAULT_BOLD
    }
    val renderer = remember(arabicTypeface) { AnatomyOverlayRenderer(arabicTypeface) }
    val trackerEngine = remember {
        AnatomyTrackerEngine { frame ->
            viewModel.onTrackingFrame(frame)
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    var touchPos by remember { mutableStateOf<Pt?>(null) }
    var animNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var activeRecorder by remember { mutableStateOf<OverlayVideoRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var lastRecordedFrameMs by remember { mutableLongStateOf(0L) }

    // Keep screen awake while camera is open
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activeRecorder?.stop()
            trackerEngine.close()
            analysisExecutor.shutdown()
        }
    }

    // Sync segmentation toggle to tracker
    LaunchedEffect(state.segEnabled) {
        trackerEngine.segmentationEnabled = state.segEnabled
    }

    // Continuous 60fps animation clock for smooth scan lines, pulse rings, and countdowns
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis {
                animNowMs = System.currentTimeMillis()
            }
        }
    }

    // Rebind CameraX whenever front/back camera toggles
    LaunchedEffect(state.isFrontCamera, previewViewRef) {
        val pv = previewViewRef ?: return@LaunchedEffect
        trackerEngine.reset()
        renderer.resetTrackingState()

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val cameraProvider = providerFuture.get()
                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()

                val preview = Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .build()
                    .also { it.surfaceProvider = pv.surfaceProvider }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, trackerEngine) }

                val selector = CameraSelector.Builder()
                    .requireLensFacing(
                        if (state.isFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    )
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
            } catch (e: Exception) {
                viewModel.onCameraError("تعذّر تشغيل الكاميرا: ${e.localizedMessage ?: "تحقق من توفر الكاميرا"}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun buildRenderSettings() = OverlayRenderSettings(
        system = state.system,
        look = state.look,
        mode = state.viewMode,
        region = state.selectedRegion,
        bodyVisibility = state.bodyVisibility,
        lensRadiusFrac = state.lensRadiusFrac,
        showLabels = state.showLabels,
        viewDirection = state.viewDirection,
        segEnabled = state.segEnabled,
        flipYaw = state.flipYaw,
        debugMode = state.debugMode,
        isFrontCamera = state.isFrontCamera,
        compactTopBotLabels = state.stageAspect == StageAspect.RATIO_9_16,
    )

    fun stopVideoRecording() {
        val rec = activeRecorder ?: return
        activeRecorder = null
        viewModel.setRecordingState(false)
        scope.launch {
            val file = rec.stop()
            if (file == null) {
                viewModel.showToast("تعذّر إتمام تسجيل الفيديو.")
                return@launch
            }
            when (val res = MediaExporter.finalizeVideoToGallery(context, file)) {
                is ExportResult.SavedToGallery -> viewModel.showToast(res.message)
                is ExportResult.SharedViaFile -> {
                    viewModel.showToast(res.message)
                    context.startActivity(
                        android.content.Intent.createChooser(
                            MediaExporter.createShareIntent(res.uri, res.mimeType),
                            "مشاركة الفيديو",
                        )
                    )
                }
                is ExportResult.Error -> viewModel.showToast(res.message)
            }
        }
    }

    fun startVideoRecordingNow(now: Long) {
        viewModel.setRecordingCountdown(false)
        val w = when (state.stageAspect) {
            StageAspect.RATIO_9_16 -> 720
            StageAspect.RATIO_16_9 -> 1280
            StageAspect.FULLSCREEN -> {
                val baseW = stageSize.width.coerceAtLeast(720)
                val baseH = stageSize.height.coerceAtLeast(720)
                val k = minOf(1f, 1080f / max(baseW, baseH))
                max(2, ((baseW * k).toInt() / 2) * 2)
            }
        }
        val h = when (state.stageAspect) {
            StageAspect.RATIO_9_16 -> 1280
            StageAspect.RATIO_16_9 -> 720
            StageAspect.FULLSCREEN -> {
                val baseW = stageSize.width.coerceAtLeast(720)
                val baseH = stageSize.height.coerceAtLeast(720)
                val k = minOf(1f, 1080f / max(baseW, baseH))
                max(2, ((baseH * k).toInt() / 2) * 2)
            }
        }
        val outDir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(outDir, "rec_${now}.mp4")
        val rec = OverlayVideoRecorder(file, w, h, arabicTypeface)
        if (rec.start()) {
            recordingFile = file
            activeRecorder = rec
            lastRecordedFrameMs = 0L
            viewModel.setRecordingState(true, now)
        } else {
            viewModel.showToast("التسجيل المباشر غير متاح على هذا الجهاز. استخدم زر اللقطة.")
        }
    }

    // Handle recording countdown & max-90s timer
    LaunchedEffect(animNowMs, state.recordingCountdown, state.isRecording) {
        if (state.recordingCountdown && animNowMs >= state.recordingCountdownEndMs) {
            startVideoRecordingNow(animNowMs)
        }
        if (state.isRecording) {
            val sec = ((animNowMs - state.recordingStartMs) / 1000L).toInt().coerceAtLeast(0)
            viewModel.updateRecordingSeconds(sec)
            if (sec >= 90) {
                stopVideoRecording()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Camera Stage Container (Fullscreen, 16:9, or 9:16)
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val stageModifier = when (state.stageAspect) {
                StageAspect.FULLSCREEN -> Modifier.fillMaxSize()
                StageAspect.RATIO_16_9 -> Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                StageAspect.RATIO_9_16 -> Modifier
                    .fillMaxHeight()
                    .aspectRatio(9f / 16f)
            }

            Box(
                modifier = stageModifier
                    .onSizeChanged { stageSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val hit = renderer.hitTest(offset.x, offset.y, viewModel.latestVisibleParts, density)
                            if (hit != null) {
                                viewModel.selectPart(hit)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> touchPos = Pt(offset.x, offset.y) },
                            onDrag = { change, _ ->
                                change.consume()
                                touchPos = Pt(change.position.x, change.position.y)
                            },
                            onDragEnd = { touchPos = null },
                            onDragCancel = { touchPos = null },
                        )
                    },
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            previewViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Anatomical AR Canvas Overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val renderSettings = buildRenderSettings()
                    val now = animNowMs
                    drawIntoCanvas { canvas ->
                        val stats = renderer.render(
                            canvas = canvas.nativeCanvas,
                            width = size.width.toInt(),
                            height = size.height.toInt(),
                            density = density,
                            nowMs = now,
                            frame = trackingFrame,
                            settings = renderSettings,
                            calibration = state.calibration,
                            calibrationSession = state.calibrationSession,
                            touchPos = touchPos,
                            highlightedPart = state.selectedPart,
                            highlightUntilMs = state.highlightUntilMs,
                            onPointerHoldPart = { part -> viewModel.selectPart(part) },
                        )
                        viewModel.onFrameRendered(
                            nowMs = now,
                            visibleParts = stats.visibleParts,
                            totalActive = stats.totalActivePartsCount,
                            detectedZoneNames = stats.detectedZoneNames,
                            isBackView = stats.isBackView,
                            torso = stats.torsoFrame,
                            poseScreen = stats.poseScreenPts,
                        )
                    }
                }

                // Feed frames into Video Recorder when recording
                LaunchedEffect(animNowMs, state.isRecording) {
                    val rec = activeRecorder ?: return@LaunchedEffect
                    if (!state.isRecording) return@LaunchedEffect
                    if (animNowMs - lastRecordedFrameMs >= 40L) {
                        lastRecordedFrameMs = animNowMs
                        val pvBmp: Bitmap? = previewViewRef?.bitmap
                        val renderSettings = buildRenderSettings()
                        rec.drawFrame(pvBmp) { c, rw, rh ->
                            renderer.render(
                                canvas = c,
                                width = rw,
                                height = rh,
                                density = density * (rw.toFloat() / max(1, stageSize.width)),
                                nowMs = animNowMs,
                                frame = trackingFrame,
                                settings = renderSettings,
                                calibration = state.calibration,
                                calibrationSession = state.calibrationSession,
                                touchPos = null,
                                highlightedPart = state.selectedPart,
                                highlightUntilMs = state.highlightUntilMs,
                                onPointerHoldPart = {},
                            )
                        }
                    }
                }

                // Recording countdown banner
                if (state.recordingCountdown) {
                    val secLeft = max(1, ceil((state.recordingCountdownEndMs - animNowMs) / 1000.0).toInt())
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xCC030C1A),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.5.dp, CyanPrimary),
                    ) {
                        Text(
                            text = secLeft.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }

        // Top Disclaimer + Status HUD Chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.disclaimer_banner),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val trackerStr = state.activeTrackers.joinToString(" · ").ifEmpty { "لا يوجد تتبع" }
                val calStr = if (state.calibration?.hasTorso == true) " · معايَر" else ""
                HudChip(
                    text = "$trackerStr · ${state.fps} إطار/ث · دون اتصال ✓$calStr",
                    borderColor = EmeraldOk.copy(alpha = 0.55f),
                )

                val zoneBanner = buildList {
                    if (state.isBackView && state.detectedZoneNames.isNotEmpty()) add("عرض خلفي")
                    addAll(state.detectedZoneNames)
                }.joinToString(" · ").ifEmpty { "ابتعد قليلًا أو ارفع يدك أمام الكاميرا" }
                HudChip(text = zoneBanner)

                HudChip(text = "الأجزاء الظاهرة: ${state.visiblePartsCount} من ${state.totalPartsCount}")

                if (state.isRecording) {
                    val mm = (state.recordingSeconds / 60).toString().padStart(2, '0')
                    val ss = (state.recordingSeconds % 60).toString().padStart(2, '0')
                    HudChip(
                        text = "● تسجيل $mm:$ss",
                        textColor = AlertRed,
                        borderColor = AlertRed.copy(alpha = 0.7f),
                    )
                }

                if (state.cameraError != null) {
                    HudChip(
                        text = state.cameraError!!,
                        textColor = GoldHighlight,
                        borderColor = GoldHighlight,
                    )
                }

                // Selected Part / Toast Info Card
                val part = state.selectedPart
                val toastMsg = state.cardMessage
                if (part != null || toastMsg != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 520.dp)
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xEE06121F),
                        border = BorderStroke(1.dp, CyanPrimary),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (part != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = part.ar,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                    Text(
                                        text = part.en,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CyanPrimary,
                                    )
                                }
                                Text(
                                    text = part.fact,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                )
                                if (part.approx) {
                                    Text(
                                        text = "تقريبي: الموضع مقدّر من أقرب نقاط الجسم",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoldHighlight,
                                    )
                                }
                            } else if (toastMsg != null) {
                                Text(
                                    text = toastMsg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Collapsible Control Sheet
        ControlSheet(
            state = state,
            onToggleExpanded = viewModel::toggleSheetExpanded,
            onSelectRegion = viewModel::setRegion,
            onSelectSystem = viewModel::setSystem,
            onSelectLook = viewModel::setLook,
            onSelectMode = viewModel::setViewMode,
            onBodyVisibilityChange = viewModel::setBodyVisibility,
            onLensRadiusChange = viewModel::setLensRadius,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onSearchSubmit = { viewModel.executeSearch() },
            onToggleTour = viewModel::toggleTour,
            onSwitchCamera = viewModel::toggleCamera,
            onToggleDebug = viewModel::toggleDebugMode,
            onSnapshot = {
                val pvBmp = previewViewRef?.bitmap
                val w = stageSize.width
                val h = stageSize.height
                val settings = buildRenderSettings()
                scope.launch {
                    val res = MediaExporter.saveSnapshot(
                        context = context,
                        previewBitmap = pvBmp,
                        width = w,
                        height = h,
                        arabicTypeface = arabicTypeface,
                    ) { c, cw, ch ->
                        renderer.render(
                            canvas = c,
                            width = cw,
                            height = ch,
                            density = density,
                            nowMs = animNowMs,
                            frame = trackingFrame,
                            settings = settings,
                            calibration = state.calibration,
                            calibrationSession = state.calibrationSession,
                            touchPos = touchPos,
                            highlightedPart = state.selectedPart,
                            highlightUntilMs = state.highlightUntilMs,
                            onPointerHoldPart = {},
                        )
                    }
                    when (res) {
                        is ExportResult.SavedToGallery -> viewModel.showToast(res.message)
                        is ExportResult.SharedViaFile -> {
                            viewModel.showToast(res.message)
                            context.startActivity(
                                android.content.Intent.createChooser(
                                    MediaExporter.createShareIntent(res.uri, res.mimeType),
                                    "مشاركة اللقطة",
                                )
                            )
                        }
                        is ExportResult.Error -> viewModel.showToast(res.message)
                    }
                }
            },
            onToggleLabels = viewModel::toggleLabels,
            onCycleViewDirection = viewModel::cycleViewDirection,
            onToggleSegmentation = viewModel::toggleSegmentation,
            onStartCalibration = viewModel::startCalibration,
            onToggleRecord = {
                when {
                    state.isRecording -> stopVideoRecording()
                    state.recordingCountdown -> viewModel.setRecordingCountdown(false)
                    else -> viewModel.setRecordingCountdown(true, System.currentTimeMillis() + 3000L)
                }
            },
            onCycleStageAspect = viewModel::cycleStageAspect,
            onToggleFlipYaw = viewModel::toggleFlipYaw,
            onToggleFilament3d = viewModel::toggleFilament3d,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (state.showFilament3d) {
            FilamentAnatomyOverlay(
                system = state.system,
                look = state.look,
                trackingFrame = trackingFrame,
                isFrontCamera = state.isFrontCamera,
                onSelectSystem = viewModel::setSystem,
                onSelectLook = viewModel::setLook,
                onClose = viewModel::toggleFilament3d,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun HudChip(
    text: String,
    textColor: Color = Color.White,
    borderColor: Color = CyanPrimary.copy(alpha = 0.35f),
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xBB000000),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 2,
        )
    }
}
