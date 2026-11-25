package com.example.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import com.example.data.AnatomyCatalog
import com.example.model.AnatomyPart
import com.example.model.AnatomySystem
import com.example.model.BodyCalibration
import com.example.model.BodyRegion
import com.example.model.BodyViewDirection
import com.example.model.CalibrationSession
import com.example.model.FacePoints
import com.example.model.Pt
import com.example.model.RenderContextData
import com.example.model.TorsoFrame
import com.example.model.TrackingFrame
import com.example.model.ViewMode
import com.example.model.VisiblePart
import com.example.model.VisualLook
import com.example.model.ZoneKey
import com.example.model.ZoneState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

data class OverlayRenderSettings(
    val system: AnatomySystem,
    val look: VisualLook,
    val mode: ViewMode,
    val region: BodyRegion,
    val bodyVisibility: Float,
    val lensRadiusFrac: Float,
    val showLabels: Boolean,
    val viewDirection: BodyViewDirection,
    val segEnabled: Boolean,
    val flipYaw: Boolean,
    val debugMode: Boolean,
    val isFrontCamera: Boolean,
    val compactTopBotLabels: Boolean,
)

data class RenderStats(
    val visibleParts: List<VisiblePart>,
    val totalActivePartsCount: Int,
    val detectedZoneNames: List<String>,
    val isBackView: Boolean,
    val torsoFrame: TorsoFrame?,
    val poseScreenPts: List<Pt>,
)

class AnatomyOverlayRenderer(
    private val arabicTypeface: Typeface = Typeface.DEFAULT_BOLD,
) {
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = arabicTypeface
    }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val path = Path()
    private val rect = RectF()

    // Temporal smoothing & 3D yaw state
    private val smoothedScreenPose = MutableList(33) { Pt(0f, 0f, 0f) }
    private var hasSmoothedPose = false
    private val holdMap = HashMap<Int, Pair<Pt, Long>>()
    private val zoneStates = ZoneKey.entries.associateWith { ZoneState() }.toMutableMap()

    private var wxsAcc = 0.5f
    private var yawCos = 1f
    private var yawSin = 0f
    private var refWfRatio = 0.8f
    private var wfReady = false
    private var wfSampleCount = 0
    private var isBackView = false

    fun resetTrackingState() {
        hasSmoothedPose = false
        holdMap.clear()
        isBackView = false
        yawCos = 1f
        yawSin = 0f
    }

    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        density: Float,
        nowMs: Long,
        frame: TrackingFrame,
        settings: OverlayRenderSettings,
        calibration: BodyCalibration?,
        calibrationSession: CalibrationSession,
        touchPos: Pt?,
        highlightedPart: AnatomyPart?,
        highlightUntilMs: Long,
        onPointerHoldPart: (AnatomyPart) -> Unit,
    ): RenderStats {
        if (width <= 0 || height <= 0) {
            return RenderStats(emptyList(), 0, emptyList(), false, null, emptyList())
        }

        val vw = frame.imageWidth.toFloat().coerceAtLeast(1f)
        val vh = frame.imageHeight.toFloat().coerceAtLeast(1f)
        val scale = max(width / vw, height / vh)
        val ox = (width - vw * scale) / 2f
        val oy = (height - vh * scale) / 2f

        fun toScreen(norm: Pt): Pt {
            val sx = ox + (if (settings.isFrontCamera) 1f - norm.x else norm.x) * vw * scale
            val sy = oy + norm.y * vh * scale
            return Pt(sx, sy, norm.v)
        }

        // Smooth pose in screen space
        if (frame.poseNorm.size == 33) {
            if (!hasSmoothedPose) {
                for (i in 0 until 33) {
                    smoothedScreenPose[i] = toScreen(frame.poseNorm[i])
                }
                hasSmoothedPose = true
            } else {
                val k = 0.45f
                for (i in 0 until 33) {
                    val target = toScreen(frame.poseNorm[i])
                    val cur = smoothedScreenPose[i]
                    smoothedScreenPose[i] = if (cur.v < 0.05f) {
                        target
                    } else {
                        Pt(
                            x = cur.x + (target.x - cur.x) * k,
                            y = cur.y + (target.y - cur.y) * k,
                            v = target.v,
                        )
                    }
                }
            }
        } else {
            hasSmoothedPose = false
        }

        fun inFrame(norm: Pt): Boolean =
            norm.x > -0.03f && norm.x < 1.03f && norm.y > -0.03f && norm.y < 1.03f

        fun pv(index: Int): Pt? {
            if (!hasSmoothedPose || frame.poseNorm.size != 33) return null
            val norm = frame.poseNorm[index]
            if (norm.v >= 0.45f && inFrame(norm)) {
                val p = smoothedScreenPose[index]
                holdMap[index] = p to nowMs
                return p
            }
            val held = holdMap[index]
            return if (held != null && nowMs - held.second < 400L) held.first else null
        }

        val screenFace = frame.faceNorm?.let { f ->
            val lEye = toScreen(f.leftEye)
            val rEye = toScreen(f.rightEye)
            val top = toScreen(f.forehead)
            val chin = toScreen(f.chin)
            val center = toScreen(f.center)
            FacePoints(
                center = center,
                height = hypot(top.x - chin.x, top.y - chin.y) * 1.28f,
                tiltRad = atan2(rEye.y - lEye.y, rEye.x - lEye.x),
                forehead = top,
                leftEye = lEye,
                rightEye = rEye,
                noseBridge = toScreen(f.noseBridge),
                noseTip = toScreen(f.noseTip),
                leftCheek = toScreen(f.leftCheek),
                rightCheek = toScreen(f.rightCheek),
                mouthLeft = toScreen(f.mouthLeft),
                mouthRight = toScreen(f.mouthRight),
                mouthCenter = toScreen(f.mouthCenter),
                chin = chin,
                leftJaw = toScreen(f.leftJaw),
                rightJaw = toScreen(f.rightJaw),
            )
        }

        val headCenter: Pt?
        val headSize: Float
        val earL = pv(7)
        val earR = pv(8)
        if (screenFace != null) {
            headCenter = screenFace.center
            headSize = screenFace.height
        } else if (earL != null && earR != null) {
            headCenter = mid(earL, earR)
            headSize = dist(earL, earR) * 1.5f
        } else {
            headCenter = null
            headSize = 0f
        }

        val shR = pv(12)
        val shL = pv(11)
        val shoulderWidth = if (shR != null && shL != null) dist(shR, shL) else 0f

        updateYaw(frame, settings.flipYaw)
        val torso = buildTorsoFrame(
            shR = shR,
            shL = shL,
            hpR = pv(24),
            hpL = pv(23),
            nose = pv(0),
            frame = frame,
            calibration = calibration,
            settings = settings,
        )

        updateZones(frame, screenFace, ::inFrame)

        val poseForContext = List(33) { idx ->
            pv(idx) ?: Pt(0f, 0f, 0f)
        }
        val renderCtx = RenderContextData(
            pose = poseForContext,
            face = screenFace,
            headCenter = headCenter,
            headSize = headSize,
            torso = torso,
            shoulderWidth = shoulderWidth,
            calibration = calibration,
        )

        val activeParts = AnatomyCatalog.parts.filter { p ->
            !((p.viewConstraint == "b" && !isBackView) || (p.viewConstraint == "f" && isBackView))
        }
        val visibleParts = ArrayList<VisiblePart>(activeParts.size)
        for (p in activeParts) {
            val res = p.locator(renderCtx)
            if (res != null && res.second > 0f) {
                visibleParts.add(VisiblePart(p, res.first, res.second))
            }
        }

        val isXray = settings.look == VisualLook.XRAY
        val boneColor = if (isXray) Color.rgb(223, 243, 255) else Color.rgb(239, 230, 204)

        val pointerTip = pv(19) ?: pv(20)
        val lensCenter = touchPos ?: pointerTip

        val scanY = ((nowMs / 14f) % (height * 1.25f))

        // Render main anatomy layer (with lens/scan clip + optional segmentation mask clip)
        canvas.save()
        var shouldDraw = true
        if (settings.mode == ViewMode.LENS) {
            if (lensCenter != null) {
                path.reset()
                path.addCircle(
                    lensCenter.x,
                    lensCenter.y,
                    settings.lensRadiusFrac * min(width, height),
                    Path.Direction.CW,
                )
                canvas.clipPath(path)
            } else {
                shouldDraw = false
            }
        } else if (settings.mode == ViewMode.SCAN) {
            canvas.clipRect(0f, 0f, width.toFloat(), scanY)
        }

        if (shouldDraw) {
            if (isXray) {
                val dimAlpha = ((0.90f * (1f - settings.bodyVisibility) + 0.05f) * 255f).toInt().coerceIn(0, 245)
                fillPaint.color = Color.argb(dimAlpha, 3, 12, 26)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
            }

            val segMask = frame.segMaskBitmap
            val useMask = settings.segEnabled && segMask != null && !segMask.isRecycled
            val layerSave = if (useMask) {
                canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            } else -1

            drawAnatomy(
                canvas = canvas,
                density = density,
                isXray = isXray,
                boneColor = boneColor,
                settings = settings,
                face = screenFace,
                headCenter = headCenter,
                headSize = headSize,
                torso = torso,
                shoulderWidth = shoulderWidth,
                calibration = calibration,
                pv = ::pv,
            )

            if (useMask && segMask != null) {
                canvas.save()
                if (settings.isFrontCamera) {
                    canvas.translate(width.toFloat(), 0f)
                    canvas.scale(-1f, 1f)
                }
                val dstX = if (settings.isFrontCamera) width - ox - vw * scale else ox
                rect.set(dstX, oy, dstX + vw * scale, oy + vh * scale)
                canvas.drawBitmap(segMask, null, rect, maskPaint)
                canvas.restore()
                canvas.restoreToCount(layerSave)
            }
        }
        canvas.restore()

        // Lens ring & scan line
        if (settings.mode == ViewMode.LENS && lensCenter != null) {
            val r = settings.lensRadiusFrac * min(width, height)
            strokePaint.color = Color.argb(90, 95, 208, 255)
            strokePaint.strokeWidth = 7f * density
            canvas.drawCircle(lensCenter.x, lensCenter.y, r, strokePaint)
            strokePaint.color = Color.rgb(95, 208, 255)
            strokePaint.strokeWidth = 3f * density
            canvas.drawCircle(lensCenter.x, lensCenter.y, r, strokePaint)
        } else if (settings.mode == ViewMode.SCAN) {
            strokePaint.color = Color.argb(100, 95, 208, 255)
            strokePaint.strokeWidth = 8f * density
            canvas.drawLine(0f, scanY, width.toFloat(), scanY, strokePaint)
            strokePaint.color = Color.rgb(159, 230, 255)
            strokePaint.strokeWidth = 3f * density
            canvas.drawLine(0f, scanY, width.toFloat(), scanY, strokePaint)
        }

        // Side callout labels
        if (settings.showLabels && settings.mode != ViewMode.LENS) {
            drawLabels(canvas, width, height, density, visibleParts, settings, scanY)
        }

        // Index finger hover auto-select (~0.5s dwell)
        if (pointerTip != null) {
            updatePointerDwell(pointerTip, width, nowMs, visibleParts, onPointerHoldPart)
        }

        // Highlight ring for tapped / searched / toured part
        if (highlightedPart != null && nowMs < highlightUntilMs) {
            val vp = visibleParts.firstOrNull { it.part == highlightedPart }
            if (vp != null) {
                val pulse = 1f + 0.15f * sin(nowMs / 120f)
                val ringR = max(vp.radius, 14f * density) * pulse
                strokePaint.color = Color.argb(95, 255, 207, 107)
                strokePaint.strokeWidth = 7f * density
                canvas.drawCircle(vp.center.x, vp.center.y, ringR, strokePaint)
                strokePaint.color = Color.rgb(255, 207, 107)
                strokePaint.strokeWidth = 3f * density
                canvas.drawCircle(vp.center.x, vp.center.y, ringR, strokePaint)

                textPaint.textSize = 16f * density
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = Color.rgb(255, 207, 107)
                val tx = vp.center.x.coerceIn(70f * density, width - 70f * density)
                val ty = max(vp.center.y - ringR * 1.25f, 46f * density)
                canvas.drawText(highlightedPart.ar, tx, ty, textPaint)
            }
        }

        // Debug overlay
        if (settings.debugMode) {
            drawDebugOverlay(canvas, height, density, frame, screenFace, torso, calibration, ::pv)
        }

        // Calibration overlay banners
        if (calibrationSession.stage == 1) {
            val sec = max(1, ((calibrationSession.targetTimeMs - nowMs + 999L) / 1000L).toInt())
            drawBanner(canvas, "قف مواجهًا الكاميرا وافتح ذراعيك قليلًا", height * 0.34f, width, density, 20f)
            drawBanner(canvas, sec.toString(), height * 0.46f, width, density, 54f)
        } else if (calibrationSession.stage == 2) {
            val msg = if (torso == null || abs(torso.cosYaw) < 0.88f || isBackView) {
                "قف مواجهًا الكاميرا"
            } else {
                "جارٍ القياس… ثبّت وضعك"
            }
            drawBanner(canvas, msg, height * 0.40f, width, density, 20f)
        }

        val detectedNames = ZoneKey.entries
            .filter { zoneStates[it]?.on == true }
            .map { it.labelAr }

        return RenderStats(
            visibleParts = visibleParts,
            totalActivePartsCount = activeParts.size,
            detectedZoneNames = detectedNames,
            isBackView = isBackView,
            torsoFrame = torso,
            poseScreenPts = poseForContext,
        )
    }

    private var lastDwellPos: Pt? = null
    private var dwellStartMs = 0L
    private var dwellFired = false

    private fun updatePointerDwell(
        tip: Pt,
        width: Int,
        nowMs: Long,
        visibleParts: List<VisiblePart>,
        onSelect: (AnatomyPart) -> Unit,
    ) {
        val last = lastDwellPos
        if (last == null || hypot(tip.x - last.x, tip.y - last.y) > width * 0.03f) {
            lastDwellPos = tip
            dwellStartMs = nowMs
            dwellFired = false
        } else if (nowMs - dwellStartMs > 500L && !dwellFired) {
            val hit = hitTest(tip.x, tip.y, visibleParts, density = 1f, excludeFingers = true)
            if (hit != null) {
                onSelect(hit)
                dwellFired = true
            }
        }
    }

    fun hitTest(
        x: Float,
        y: Float,
        visibleParts: List<VisiblePart>,
        density: Float,
        excludeFingers: Boolean = false,
    ): AnatomyPart? {
        var best: VisiblePart? = null
        for (vp in visibleParts) {
            if (excludeFingers && (vp.part.ar.startsWith("الإبهام") || vp.part.ar.startsWith("السبابة") || vp.part.ar.startsWith("الخنصر"))) {
                continue
            }
            val d = hypot(vp.center.x - x, vp.center.y - y)
            if (d <= max(vp.radius * 1.15f, 12f * density)) {
                if (best == null || vp.radius < best.radius) {
                    best = vp
                }
            }
        }
        return best?.part
    }

    private fun updateYaw(frame: TrackingFrame, flipYaw: Boolean) {
        if (!frame.has3dYaw || frame.poseNorm.size < 25) return
        val idx = frame.poseNorm[11].x - frame.poseNorm[12].x
        var dx = frame.yawDx
        var dz = frame.yawDz
        if (frame.hipVisibility >= 0.45f) {
            dx += frame.hipYawDx * 0.6f
            dz += frame.hipYawDz * 0.6f
        }
        if (abs(idx) > 0.04f && abs(frame.yawDx) > 0.05f) {
            val sgn = sign(idx) * sign(frame.yawDx)
            wxsAcc += (sgn - wxsAcc) * 0.05f
        }
        val wxs = if (wxsAcc >= 0f) 1f else -1f
        dx *= wxs
        dz *= wxs
        if (flipYaw) dz = -dz
        val n = hypot(dx, dz).takeIf { it > 0.0001f } ?: 1f
        yawCos += (dx / n - yawCos) * 0.4f
        yawSin += (dz / n - yawSin) * 0.4f
        val m = hypot(yawCos, yawSin).takeIf { it > 0.0001f } ?: 1f
        yawCos /= m
        yawSin /= m
    }

    private fun buildTorsoFrame(
        shR: Pt?,
        shL: Pt?,
        hpR: Pt?,
        hpL: Pt?,
        nose: Pt?,
        frame: TrackingFrame,
        calibration: BodyCalibration?,
        settings: OverlayRenderSettings,
    ): TorsoFrame? {
        if (shR == null || shL == null || hpR == null || hpL == null) return null
        val sm = Pt((shR.x + shL.x) / 2f, (shR.y + shL.y) / 2f)
        val hm = Pt((hpR.x + hpL.x) / 2f, (hpR.y + hpL.y) / 2f)
        val len = dist(sm, hm)
        if (len < 8f) return null

        val vx = (hm.x - sm.x) / len
        val vy = (hm.y - sm.y) / len
        val sg = if (settings.isFrontCamera) -1f else 1f
        val axisE = Pt(vy * sg, -vx * sg)

        if (calibration != null && calibration.hasTorso) {
            refWfRatio = calibration.rW
            wfReady = true
        } else if (abs(yawCos) > 0.85f) {
            val raw = dist(shR, shL) / (abs(yawCos) * len)
            refWfRatio = (refWfRatio + (raw - refWfRatio) * 0.08f).coerceIn(0.5f, 1.2f)
            if (++wfSampleCount > 20) wfReady = true
        }

        var c = yawCos
        var s = yawSin
        if (!frame.has3dYaw) {
            val cm = if (wfReady) min(1f, dist(shR, shL) / (refWfRatio * len)) else 1f
            val off = if (nose != null) ((nose.x - sm.x) * axisE.x + (nose.y - sm.y) * axisE.y) / (refWfRatio * len) else 0f
            c = cm
            s = (if (off >= 0f) 1f else -1f) * sqrt(max(0f, 1f - cm * cm))
        } else if (wfReady) {
            val rr = min(1f, dist(shR, shL) / (refWfRatio * len))
            val cm = 0.5f * abs(c) + 0.5f * rr
            c = (if (c < 0f) -1f else 1f) * cm
            s = (if (s < 0f) -1f else 1f) * sqrt(max(0f, 1f - cm * cm))
        }

        isBackView = when (settings.viewDirection) {
            BodyViewDirection.FRONT -> false
            BodyViewDirection.BACK -> true
            BodyViewDirection.AUTO -> {
                if (!isBackView && c < -0.35f && frame.faceNorm == null) true
                else if (isBackView && (c > -0.1f || frame.faceNorm != null)) false
                else isBackView
            }
        }

        return TorsoFrame(
            a = shR,
            b = shL,
            c = hpR,
            e = hpL,
            sm = sm,
            hm = hm,
            length = len,
            axisE = axisE,
            wf = refWfRatio * len,
            cosYaw = c,
            sinYaw = s,
        )
    }

    private fun updateZones(frame: TrackingFrame, face: FacePoints?, inFrame: (Pt) -> Boolean) {
        val pose = frame.poseNorm
        fun checkPose(indices: IntArray, zone: ZoneKey) {
            if (pose.size != 33) {
                updZone(zone, false)
                return
            }
            var m = 1f
            var ok = true
            for (i in indices) {
                val p = pose[i]
                m = min(m, p.v)
                if (!inFrame(p)) ok = false
            }
            val thresh = if (zoneStates[zone]?.on == true) 0.4f else 0.55f
            updZone(zone, ok && m >= thresh)
        }

        checkPose(intArrayOf(0, 11, 12), ZoneKey.NECK)
        checkPose(intArrayOf(11, 12, 23, 24), ZoneKey.TORSO)
        checkPose(intArrayOf(23, 24), ZoneKey.PELVIS)
        checkPose(intArrayOf(12, 14), ZoneKey.ARM_R)
        checkPose(intArrayOf(11, 13), ZoneKey.ARM_L)
        checkPose(intArrayOf(14, 16), ZoneKey.FOREARM_R)
        checkPose(intArrayOf(13, 15), ZoneKey.FOREARM_L)
        checkPose(intArrayOf(24, 26), ZoneKey.THIGH_R)
        checkPose(intArrayOf(23, 25), ZoneKey.THIGH_L)
        checkPose(intArrayOf(26, 28), ZoneKey.SHIN_R)
        checkPose(intArrayOf(25, 27), ZoneKey.SHIN_L)
        checkPose(intArrayOf(28, 32, 30), ZoneKey.FOOT_R)
        checkPose(intArrayOf(27, 31, 29), ZoneKey.FOOT_L)

        val earsOk = pose.size == 33 && pose[7].v >= 0.45f && pose[8].v >= 0.45f && inFrame(pose[7]) && inFrame(pose[8])
        val noseOk = pose.size == 33 && pose[0].v >= 0.45f && inFrame(pose[0])
        updZone(ZoneKey.HEAD, face != null || noseOk || earsOk)

        val hrOk = pose.size == 33 && pose[16].v >= 0.45f && inFrame(pose[16])
        val hlOk = pose.size == 33 && pose[15].v >= 0.45f && inFrame(pose[15])
        updZone(ZoneKey.HAND_R, hrOk)
        updZone(ZoneKey.HAND_L, hlOk)
    }

    private fun updZone(zone: ZoneKey, ok: Boolean) {
        val cur = zoneStates[zone] ?: ZoneState()
        val target = if (ok) 1f else 0f
        zoneStates[zone] = ZoneState(on = ok, alpha = cur.alpha + (target - cur.alpha) * 0.18f)
    }

    private fun drawAnatomy(
        canvas: Canvas,
        density: Float,
        isXray: Boolean,
        boneColor: Int,
        settings: OverlayRenderSettings,
        face: FacePoints?,
        headCenter: Pt?,
        headSize: Float,
        torso: TorsoFrame?,
        shoulderWidth: Float,
        calibration: BodyCalibration?,
        pv: (Int) -> Pt?,
    ) {
        val drawBones = settings.system == AnatomySystem.ALL || settings.system == AnatomySystem.BONES
        val drawOrgans = settings.system == AnatomySystem.ALL || settings.system == AnatomySystem.ORGANS
        val drawMuscles = settings.system == AnatomySystem.ALL || settings.system == AnatomySystem.MUSCLES

        val wb = torso?.wf
            ?: shoulderWidth.takeIf { it > 0f }
            ?: (headSize * 1.6f).takeIf { it > 0f }
            ?: (150f * density)
        val lw = max(wb * 0.045f, 2f * density)
        val muscleColor = if (isXray) Color.argb(62, 255, 120, 120) else Color.argb(145, 190, 40, 40)

        fun organColor(r: Int, g: Int, b: Int, alpha: Int = 215): Int =
            if (isXray) Color.argb(85, 95, 208, 255) else Color.argb(alpha, r, g, b)

        fun zoneAlpha(zone: ZoneKey, region: BodyRegion): Float {
            val z = zoneStates[zone]?.alpha ?: 0f
            val isoMult = if (settings.region == BodyRegion.ALL || settings.region == region) 1f else 0.12f
            return (z * isoMult).coerceIn(0f, 1f)
        }

        fun drawLine(a: Pt?, b: Pt?, width: Float, color: Int, alpha: Float) {
            if (a == null || b == null || alpha <= 0.02f) return
            if (isXray && color == boneColor) {
                strokePaint.color = applyAlpha(Color.rgb(95, 208, 255), alpha * 0.35f)
                strokePaint.strokeWidth = width * 2.1f
                canvas.drawLine(a.x, a.y, b.x, b.y, strokePaint)
            }
            strokePaint.color = applyAlpha(color, alpha)
            strokePaint.strokeWidth = width
            canvas.drawLine(a.x, a.y, b.x, b.y, strokePaint)
        }

        fun drawEllipse(
            p: Pt?,
            rx: Float,
            ry: Float,
            angleRad: Float,
            fillCol: Int?,
            strokeCol: Int?,
            strokeW: Float,
            alpha: Float,
        ) {
            if (p == null || alpha <= 0.02f) return
            val erx = max(rx, 1f)
            val ery = max(ry, 1f)
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate((angleRad * 180f / PI).toFloat())
            rect.set(-erx, -ery, erx, ery)
            if (fillCol != null) {
                fillPaint.color = applyAlpha(fillCol, alpha)
                canvas.drawOval(rect, fillPaint)
            }
            if (strokeCol != null) {
                if (isXray && strokeCol == boneColor) {
                    strokePaint.color = applyAlpha(Color.rgb(95, 208, 255), alpha * 0.32f)
                    strokePaint.strokeWidth = strokeW * 2.0f
                    canvas.drawOval(rect, strokePaint)
                }
                strokePaint.color = applyAlpha(strokeCol, alpha)
                strokePaint.strokeWidth = strokeW
                canvas.drawOval(rect, strokePaint)
            }
            canvas.restore()
        }

        // 1. Head & Skull
        val headAlpha = zoneAlpha(ZoneKey.HEAD, BodyRegion.HEAD)
        if (headCenter != null && headAlpha > 0.02f) {
            val an = face?.tiltRad ?: 0f
            if (drawBones) {
                drawEllipse(headCenter, headSize * 0.38f, headSize * 0.50f, an, null, boneColor, lw * 0.8f, headAlpha)
                if (face != null) {
                    drawEllipse(face.rightEye, headSize * 0.075f, headSize * 0.07f, an, Color.rgb(0, 0, 18), boneColor, lw * 0.5f, headAlpha)
                    drawEllipse(face.leftEye, headSize * 0.075f, headSize * 0.07f, an, Color.rgb(0, 0, 18), boneColor, lw * 0.5f, headAlpha)
                    drawLine(face.noseBridge, face.noseTip, lw * 0.9f, boneColor, headAlpha)

                    path.reset()
                    path.moveTo(face.rightJaw.x, face.rightJaw.y)
                    path.quadTo(face.chin.x, face.chin.y + headSize * 0.04f, face.leftJaw.x, face.leftJaw.y)
                    strokePaint.color = applyAlpha(boneColor, headAlpha)
                    strokePaint.strokeWidth = lw * 0.8f
                    canvas.drawPath(path, strokePaint)

                    drawLine(face.mouthRight, face.mouthLeft, lw * 1.3f, Color.WHITE, headAlpha)
                }
            }
            if (drawOrgans && face != null) {
                val brainCenter = lerp(headCenter, face.forehead, 0.3f)
                drawEllipse(brainCenter, headSize * 0.30f, headSize * 0.22f, an, organColor(235, 150, 170, 130), null, 0f, headAlpha)
            }
        }

        // 2. Neck (7 cervical vertebrae)
        val neckMid = mid(pv(11), pv(12))
        val neckAlpha = zoneAlpha(ZoneKey.NECK, BodyRegion.NECK)
        if (neckMid != null && headCenter != null && drawBones && neckAlpha > 0.02f) {
            val topNeck = face?.chin ?: lerp(headCenter, neckMid, 0.5f)
            for (i in 0 until 7) {
                drawEllipse(lerp(topNeck, neckMid, 0.25f + i * 0.1f), lw * 0.9f, lw * 0.5f, 0f, boneColor, null, 0f, neckAlpha)
            }
        }

        // 3. 3D Torso
        if (torso != null) {
            val ca = abs(torso.cosYaw)
            val sa = abs(torso.sinYaw)
            val rotAngle = atan2(torso.axisE.y, torso.axisE.x)
            fun rx(rw: Float, rd: Float): Float = rw * wb * ca + rd * wb * sa

            // Chest
            val chestAlpha = zoneAlpha(ZoneKey.TORSO, BodyRegion.CHEST)
            if (chestAlpha > 0.02f) {
                if (drawBones) {
                    for (i in 0 until 12) {
                        val b = 0.07f + i * 0.034f
                        val tq = 0.55f + 0.45f * sin(PI.toFloat() * (i + 1.5f) / 13.5f)
                        val rw = 0.40f * tq
                        val rd = 0.27f * tq
                        val endRad = (if (i < 7) 80f else if (i < 10) 60f else 35f) * 0.01745f
                        for (sideSign in intArrayOf(-1, 1)) {
                            for (pass in 0..1) {
                                val ribAlpha = chestAlpha * (if (pass == 1) 1f else 0.45f)
                                path.reset()
                                var started = false
                                for (k in 0..10) {
                                    val ph = -1.4835f + (endRad + 1.4835f) * k / 10f
                                    if (if (pass == 1) ph < 0f else ph > 0.15f) continue
                                    val p = torso.project(0.5f + sideSign * rw * cos(ph), b + 0.035f * k / 10f, rd * sin(ph))
                                    if (!started) {
                                        path.moveTo(p.x, p.y)
                                        started = true
                                    } else {
                                        path.lineTo(p.x, p.y)
                                    }
                                }
                                if (started) {
                                    strokePaint.color = applyAlpha(boneColor, ribAlpha)
                                    strokePaint.strokeWidth = lw * 0.5f
                                    canvas.drawPath(path, strokePaint)
                                }
                            }
                        }
                    }
                    drawLine(torso.project(0.5f, 0.06f, 0.27f), torso.project(0.5f, 0.27f, 0.27f), lw * 0.9f, boneColor, chestAlpha)
                }

                if (drawOrgans) {
                    val lungStroke = if (isXray) boneColor else null
                    drawEllipse(torso.project(0.30f, 0.20f, -0.02f), rx(0.15f, 0.12f), wb * 0.26f, rotAngle, organColor(230, 140, 155, 135), lungStroke, lw * 0.3f, chestAlpha)
                    drawEllipse(torso.project(0.72f, 0.20f, -0.02f), rx(0.13f, 0.12f), wb * 0.26f, rotAngle, organColor(230, 140, 155, 135), lungStroke, lw * 0.3f, chestAlpha)
                    drawEllipse(torso.project(0.62f, 0.26f, 0.08f), rx(0.08f, 0.07f), wb * 0.10f, rotAngle, organColor(192, 24, 47), null, 0f, chestAlpha)

                    val d0 = torso.project(0.12f, 0.37f)
                    val d1 = torso.project(0.50f, 0.31f)
                    val d2 = torso.project(0.88f, 0.37f)
                    path.reset()
                    path.moveTo(d0.x, d0.y)
                    path.quadTo(d1.x, d1.y - wb * 0.03f, d2.x, d2.y)
                    strokePaint.color = applyAlpha(organColor(230, 200, 150, 210), chestAlpha)
                    strokePaint.strokeWidth = lw * 0.8f
                    canvas.drawPath(path, strokePaint)
                }

                if (drawMuscles && !isBackView) {
                    drawEllipse(torso.project(0.34f, 0.14f, 0.25f), rx(0.20f, 0.08f), wb * 0.09f, rotAngle, muscleColor, null, 0f, chestAlpha)
                    drawEllipse(torso.project(0.66f, 0.14f, 0.25f), rx(0.20f, 0.08f), wb * 0.09f, rotAngle, muscleColor, null, 0f, chestAlpha)
                }
            }

            // Clavicles & Shoulder joints
            val shAlpha = zoneAlpha(ZoneKey.TORSO, BodyRegion.SHOULDERS)
            if (drawBones && shAlpha > 0.02f) {
                val m = torso.project(0.5f, 0.04f, 0.2f)
                drawLine(torso.a, m, lw * 0.8f, boneColor, shAlpha)
                drawLine(torso.b, m, lw * 0.8f, boneColor, shAlpha)
                drawEllipse(torso.a, lw * 1.1f, lw * 1.1f, 0f, boneColor, null, 0f, shAlpha)
                drawEllipse(torso.b, lw * 1.1f, lw * 1.1f, 0f, boneColor, null, 0f, shAlpha)
            }

            // Abdomen
            val abdAlpha = zoneAlpha(ZoneKey.TORSO, BodyRegion.ABDOMEN)
            if (abdAlpha > 0.02f) {
                if (drawOrgans) {
                    drawEllipse(torso.project(0.30f, 0.46f, 0.03f), rx(0.18f, 0.10f), wb * 0.09f, rotAngle, organColor(138, 59, 31), null, 0f, abdAlpha)
                    drawEllipse(torso.project(0.68f, 0.46f, 0.05f), rx(0.10f, 0.08f), wb * 0.09f, rotAngle, organColor(217, 138, 106), null, 0f, abdAlpha)
                    drawEllipse(torso.project(0.82f, 0.42f, -0.12f), rx(0.05f, 0.04f), wb * 0.07f, rotAngle, organColor(122, 47, 74), null, 0f, abdAlpha)

                    path.reset()
                    for (i in 0..8) {
                        val p = torso.project(0.30f + i * 0.05f, 0.62f + (i and 1) * 0.10f + (i shr 1) * 0.02f, 0.05f)
                        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    strokePaint.color = applyAlpha(organColor(230, 169, 143), abdAlpha)
                    strokePaint.strokeWidth = lw * 1.6f
                    canvas.drawPath(path, strokePaint)
                }
                if (drawMuscles && !isBackView) {
                    for (r in 0..3) {
                        for (a in floatArrayOf(0.45f, 0.55f)) {
                            drawEllipse(torso.project(a, 0.38f + r * 0.10f, 0.27f), rx(0.07f, 0.04f), wb * 0.04f, rotAngle, muscleColor, null, 0f, abdAlpha)
                        }
                    }
                }
            }

            // Back (Spine, Scapulae, Kidneys, Back Muscles)
            val backAlpha = zoneAlpha(ZoneKey.TORSO, BodyRegion.BACK)
            if (backAlpha > 0.02f) {
                if (drawBones) {
                    for (i in 0 until 17) {
                        drawEllipse(torso.project(0.5f, 0.04f + i * 0.056f, -0.22f), lw * 0.8f, lw * 0.55f, rotAngle, boneColor, null, 0f, backAlpha)
                    }
                    if (isBackView) {
                        drawEllipse(torso.project(0.27f, 0.14f, -0.27f), rx(0.09f, 0.03f), wb * 0.11f, rotAngle + 0.35f, null, boneColor, lw * 0.5f, backAlpha)
                        drawEllipse(torso.project(0.73f, 0.14f, -0.27f), rx(0.09f, 0.03f), wb * 0.11f, rotAngle - 0.35f, null, boneColor, lw * 0.5f, backAlpha)
                    }
                }
                if (drawOrgans) {
                    drawEllipse(torso.project(0.36f, 0.50f, -0.17f), rx(0.05f, 0.04f), wb * 0.07f, rotAngle, organColor(155, 61, 61), null, 0f, backAlpha)
                    drawEllipse(torso.project(0.64f, 0.50f, -0.17f), rx(0.05f, 0.04f), wb * 0.07f, rotAngle, organColor(155, 61, 61), null, 0f, backAlpha)
                }
                if (drawMuscles && isBackView) {
                    val trap = listOf(0.5f to -0.03f, 0.08f to 0.05f, 0.3f to 0.3f, 0.5f to 0.42f, 0.7f to 0.3f, 0.92f to 0.05f)
                    path.reset()
                    trap.forEachIndexed { i, (a, b) ->
                        val p = torso.project(a, b, -0.25f)
                        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    path.close()
                    fillPaint.color = applyAlpha(muscleColor, backAlpha)
                    canvas.drawPath(path, fillPaint)
                    drawEllipse(torso.project(0.20f, 0.48f, -0.25f), rx(0.12f, 0.05f), wb * 0.14f, rotAngle, muscleColor, null, 0f, backAlpha)
                    drawEllipse(torso.project(0.80f, 0.48f, -0.25f), rx(0.12f, 0.05f), wb * 0.14f, rotAngle, muscleColor, null, 0f, backAlpha)
                }
            }

            // Pelvis
            val pelAlpha = zoneAlpha(ZoneKey.PELVIS, BodyRegion.PELVIS)
            if (pelAlpha > 0.02f) {
                if (drawBones) {
                    drawEllipse(torso.project(0.10f, 0.93f), rx(0.20f, 0.08f), wb * 0.14f, rotAngle, null, boneColor, lw * 0.6f, pelAlpha)
                    drawEllipse(torso.project(0.90f, 0.93f), rx(0.20f, 0.08f), wb * 0.14f, rotAngle, null, boneColor, lw * 0.6f, pelAlpha)
                    drawEllipse(torso.project(0.50f, 0.97f, -0.17f), rx(0.06f, 0.05f), wb * 0.09f, rotAngle, null, boneColor, lw * 0.6f, pelAlpha)
                }
                if (drawOrgans) {
                    drawEllipse(torso.project(0.50f, 0.96f, 0.06f), rx(0.06f, 0.05f), wb * 0.06f, rotAngle, organColor(216, 194, 90), null, 0f, pelAlpha)
                }
                if (drawMuscles && isBackView) {
                    drawEllipse(torso.project(0.30f, 1.07f, -0.20f), rx(0.14f, 0.06f), wb * 0.12f, rotAngle, muscleColor, null, 0f, pelAlpha)
                    drawEllipse(torso.project(0.70f, 1.07f, -0.20f), rx(0.14f, 0.06f), wb * 0.12f, rotAngle, muscleColor, null, 0f, pelAlpha)
                }
            }
        }

        // 4. Arms & Hands (with calibration limb-length clamping)
        fun clampLimb(s: Pt?, e0: Pt?, w0: Pt?, ratio1: Float, ratio2: Float): Triple<Pt?, Pt?, Pt?> {
            if (s == null || e0 == null) return Triple(s, e0, w0)
            val max1 = if (ratio1 > 0f && torso != null) ratio1 * torso.length * 1.05f else 0f
            val e = clampTo(s, e0, max1)
            var w = w0
            if (w0 != null && e != null) {
                val shifted = Pt(w0.x + (e.x - e0.x), w0.y + (e.y - e0.y))
                val max2 = if (ratio2 > 0f && torso != null) ratio2 * torso.length * 1.05f else 0f
                w = clampTo(e, shifted, max2)
            }
            return Triple(s, e, w)
        }

        data class ArmDrawSpec(val sh: Int, val el: Int, val wr: Int, val pk: Int, val ix: Int, val th: Int, val zArm: ZoneKey, val zFore: ZoneKey, val zHand: ZoneKey)
        listOf(
            ArmDrawSpec(12, 14, 16, 18, 20, 22, ZoneKey.ARM_R, ZoneKey.FOREARM_R, ZoneKey.HAND_R),
            ArmDrawSpec(11, 13, 15, 17, 19, 21, ZoneKey.ARM_L, ZoneKey.FOREARM_L, ZoneKey.HAND_L),
        ).forEach { sp ->
            val (s, e, w) = clampLimb(pv(sp.sh), pv(sp.el), pv(sp.wr), calibration?.rA ?: 0f, calibration?.rF ?: 0f)
            val aAlpha = zoneAlpha(sp.zArm, BodyRegion.ARMS)
            if (s != null && e != null && aAlpha > 0.02f) {
                if (drawBones) {
                    drawLine(s, e, lw * 1.4f, boneColor, aAlpha)
                    drawEllipse(e, lw * 1.3f, lw * 1.3f, 0f, boneColor, null, 0f, aAlpha)
                }
                if (drawMuscles) {
                    drawLine(lerp(s, e, 0.15f), lerp(s, e, 0.85f), lw * 3.4f, muscleColor, aAlpha)
                    drawEllipse(s, lw * 2.2f, lw * 2.2f, 0f, muscleColor, null, 0f, aAlpha)
                }
            }
            val fAlpha = zoneAlpha(sp.zFore, BodyRegion.FOREARMS)
            if (e != null && w != null && fAlpha > 0.02f) {
                val dx = w.x - e.x
                val dy = w.y - e.y
                val l = hypot(dx, dy).takeIf { it > 0.001f } ?: 1f
                val ox = -dy / l * lw * 0.9f
                val oy = dx / l * lw * 0.9f
                if (drawBones) {
                    drawLine(Pt(e.x + ox, e.y + oy), Pt(w.x + ox, w.y + oy), lw * 0.8f, boneColor, fAlpha)
                    drawLine(Pt(e.x - ox, e.y - oy), Pt(w.x - ox, w.y - oy), lw * 0.8f, boneColor, fAlpha)
                }
                if (drawMuscles) {
                    drawLine(lerp(e, w, 0.1f), lerp(e, w, 0.8f), lw * 2.6f, muscleColor, fAlpha)
                }
            }
            val hAlpha = zoneAlpha(sp.zHand, BodyRegion.HANDS)
            if (w != null && drawBones && hAlpha > 0.02f) {
                drawEllipse(w, lw * 0.9f, lw * 0.7f, 0f, boneColor, null, 0f, hAlpha)
                for (fi in intArrayOf(sp.pk, sp.ix, sp.th)) {
                    val fp = pv(fi)
                    if (fp != null) {
                        drawLine(w, fp, lw * 0.6f, boneColor, hAlpha)
                        drawEllipse(fp, lw * 0.45f, lw * 0.45f, 0f, boneColor, null, 0f, hAlpha)
                    }
                }
            }
        }

        // 5. Legs & Feet
        data class LegDrawSpec(val hp: Int, val kn: Int, val ak: Int, val hl: Int, val toe: Int, val zTh: ZoneKey, val zSh: ZoneKey, val zFt: ZoneKey)
        listOf(
            LegDrawSpec(24, 26, 28, 30, 32, ZoneKey.THIGH_R, ZoneKey.SHIN_R, ZoneKey.FOOT_R),
            LegDrawSpec(23, 25, 27, 29, 31, ZoneKey.THIGH_L, ZoneKey.SHIN_L, ZoneKey.FOOT_L),
        ).forEach { sp ->
            val (hp, k, an) = clampLimb(pv(sp.hp), pv(sp.kn), pv(sp.ak), calibration?.rT ?: 0f, calibration?.rS ?: 0f)
            val tAlpha = zoneAlpha(sp.zTh, BodyRegion.THIGHS)
            if (hp != null && k != null && tAlpha > 0.02f) {
                if (drawBones) {
                    drawLine(hp, k, lw * 1.8f, boneColor, tAlpha)
                    drawEllipse(hp, lw * 1.5f, lw * 1.5f, 0f, boneColor, null, 0f, tAlpha)
                }
                if (drawMuscles) {
                    drawLine(lerp(hp, k, 0.12f), lerp(hp, k, 0.85f), lw * 4.6f, muscleColor, tAlpha)
                }
            }
            val sAlpha = zoneAlpha(sp.zSh, BodyRegion.SHINS)
            if (k != null && an != null && sAlpha > 0.02f) {
                val dx = an.x - k.x
                val dy = an.y - k.y
                val l = hypot(dx, dy).takeIf { it > 0.001f } ?: 1f
                val ox = -dy / l * lw * 0.8f
                val oy = dx / l * lw * 0.8f
                if (drawBones) {
                    drawEllipse(k, lw * 1.3f, lw * 1.6f, 0f, null, boneColor, lw * 0.6f, sAlpha)
                    drawLine(k, an, lw * 1.4f, boneColor, sAlpha)
                    drawLine(Pt(k.x + ox, k.y + oy), Pt(an.x + ox, an.y + oy), lw * 0.6f, boneColor, sAlpha)
                }
                if (drawMuscles) {
                    drawLine(lerp(k, an, 0.15f), lerp(k, an, 0.70f), lw * 3.6f, muscleColor, sAlpha)
                }
            }
            val ftAlpha = zoneAlpha(sp.zFt, BodyRegion.FEET)
            val hh = pv(sp.hl)
            val tt = pv(sp.toe)
            if (an != null && tt != null && hh != null && drawBones && ftAlpha > 0.02f) {
                drawLine(an, hh, lw, boneColor, ftAlpha)
                drawLine(hh, tt, lw * 1.1f, boneColor, ftAlpha)
                val dx = tt.x - hh.x
                val dy = tt.y - hh.y
                for (i in -2..2) {
                    drawLine(tt, Pt(tt.x + dx * 0.18f - dy * 0.05f * i, tt.y + dy * 0.18f + dx * 0.05f * i), lw * 0.5f, boneColor, ftAlpha)
                }
            }
        }
    }

    private fun drawLabels(
        canvas: Canvas,
        width: Int,
        height: Int,
        density: Float,
        visibleParts: List<VisiblePart>,
        settings: OverlayRenderSettings,
        scanY: Float,
    ) {
        val isAll = settings.region == BodyRegion.ALL
        var candidates = visibleParts.filter { vp ->
            val p = vp.part
            val matchGroup = if (isAll) p.priority > 0f else p.group == settings.region
            val inScan = settings.mode != ViewMode.SCAN || vp.center.y <= scanY
            matchGroup && inScan
        }
        if (isAll) candidates = candidates.sortedBy { it.part.priority }
        candidates = candidates.take(if (isAll) 16 else 14)
        if (candidates.isEmpty()) return

        val top = if (settings.compactTopBotLabels) height * 0.14f else height * 0.11f
        val bot = if (settings.compactTopBotLabels) height * 0.82f else height * 0.85f
        val gap = 25f * density
        val fs = 13f * density

        textPaint.textSize = fs
        textPaint.textAlign = Paint.Align.LEFT

        for (side in 0..1) {
            val sideList = candidates
                .filter { (it.center.x <= width / 2f) == (side == 0) }
                .sortedBy { it.center.y }
            if (sideList.isEmpty()) continue

            val ys = FloatArray(sideList.size) { sideList[it].center.y.coerceIn(top, bot) }
            for (i in 1 until ys.size) {
                if (ys[i] < ys[i - 1] + gap) ys[i] = ys[i - 1] + gap
            }
            for (i in ys.lastIndex downTo 0) {
                val mx = if (i == ys.lastIndex) bot else ys[i + 1] - gap
                if (ys[i] > mx) ys[i] = mx
            }
            for (i in 1 until ys.size) {
                if (ys[i] < ys[i - 1] + gap) ys[i] = ys[i - 1] + gap
            }

            sideList.forEachIndexed { idx, vp ->
                val p = vp.part
                val tw = textPaint.measureText(p.ar)
                val sf = if (p.approx) textPaint.measureText(" ~") else 0f
                val boxW = tw + sf + 12f * density
                val x0 = if (side == 1) width - 6f * density - boxW else 6f * density
                val xe = if (side == 1) x0 else x0 + boxW
                val y = ys[idx]

                strokePaint.color = Color.argb(215, 159, 230, 255)
                strokePaint.strokeWidth = 1.2f * density
                canvas.drawLine(vp.center.x, vp.center.y, xe, y, strokePaint)

                fillPaint.color = Color.rgb(159, 230, 255)
                canvas.drawCircle(vp.center.x, vp.center.y, 2.5f * density, fillPaint)

                fillPaint.color = Color.argb(200, 3, 12, 26)
                rect.set(x0, y - fs * 0.8f, x0 + boxW, y + fs * 0.8f)
                canvas.drawRoundRect(rect, 6f * density, 6f * density, fillPaint)

                val fm = textPaint.fontMetrics
                val textY = y - (fm.ascent + fm.descent) / 2f
                textPaint.color = Color.WHITE
                canvas.drawText(p.ar, x0 + 6f * density, textY, textPaint)
                if (p.approx) {
                    textPaint.color = Color.rgb(255, 207, 107)
                    canvas.drawText(" ~", x0 + 6f * density + tw, textY, textPaint)
                }
            }
        }
    }

    private fun drawDebugOverlay(
        canvas: Canvas,
        height: Int,
        density: Float,
        frame: TrackingFrame,
        face: FacePoints?,
        torso: TorsoFrame?,
        calibration: BodyCalibration?,
        pv: (Int) -> Pt?,
    ) {
        textPaint.textSize = 10f * density
        textPaint.textAlign = Paint.Align.LEFT
        for (i in 0 until 33) {
            val pt = pv(i) ?: continue
            fillPaint.color = if (pt.v >= 0.5f) Color.GREEN else Color.RED
            canvas.drawRect(pt.x - 3f, pt.y - 3f, pt.x + 3f, pt.y + 3f, fillPaint)
            textPaint.color = Color.WHITE
            canvas.drawText("$i", pt.x + 5f, pt.y, textPaint)
        }
        if (face != null) {
            fillPaint.color = Color.YELLOW
            listOf(face.leftEye, face.rightEye, face.noseTip, face.chin, face.mouthCenter).forEach {
                canvas.drawCircle(it.x, it.y, 3f * density, fillPaint)
            }
        }
        if (torso != null) {
            textPaint.textSize = 13f * density
            textPaint.color = Color.YELLOW
            val yawDeg = (atan2(torso.sinYaw, torso.cosYaw) * 180f / PI).toInt()
            val calTag = if (calibration?.hasTorso == true) " CAL" else ""
            canvas.drawText("yaw ${yawDeg}°  Wf=${torso.wf.toInt()}$calTag", 12f * density, height * 0.5f, textPaint)
        }
    }

    private fun drawBanner(canvas: Canvas, text: String, y: Float, width: Int, density: Float, sp: Float) {
        val fs = sp * density
        textPaint.textSize = fs
        textPaint.textAlign = Paint.Align.CENTER
        val tw = textPaint.measureText(text) + 28f * density
        fillPaint.color = Color.argb(195, 3, 12, 26)
        rect.set(width / 2f - tw / 2f, y - fs * 0.8f, width / 2f + tw / 2f, y + fs * 0.8f)
        canvas.drawRoundRect(rect, 12f * density, 12f * density, fillPaint)
        textPaint.color = Color.WHITE
        val fm = textPaint.fontMetrics
        canvas.drawText(text, width / 2f, y - (fm.ascent + fm.descent) / 2f, textPaint)
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val baseA = Color.alpha(color)
        val finalA = (baseA * alpha).toInt().coerceIn(0, 255)
        return Color.argb(finalA, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun clampTo(a: Pt, b: Pt, maxLen: Float): Pt {
        if (maxLen <= 0f) return b
        val d = dist(a, b)
        if (d <= maxLen) return b
        val k = maxLen / d
        return Pt(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, b.v)
    }

    private fun dist(a: Pt, b: Pt): Float = hypot(a.x - b.x, a.y - b.y)
    private fun mid(a: Pt?, b: Pt?): Pt? = if (a != null && b != null) Pt((a.x + b.x) / 2f, (a.y + b.y) / 2f) else null
    private fun lerp(a: Pt, b: Pt, t: Float): Pt = Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}
