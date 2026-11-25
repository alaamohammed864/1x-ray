package com.example.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.model.FacePoints
import com.example.model.Pt
import com.example.model.Pt3
import com.example.model.TrackingFrame
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.hypot

class AnatomyTrackerEngine(
    private val onFrame: (TrackingFrame) -> Unit,
) : ImageAnalysis.Analyzer {

    private val poseDetector: PoseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    private val faceDetector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .enableTracking()
            .build()
    )

    private val segmenter: Segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .build()
    )

    @Volatile
    var segmentationEnabled: Boolean = false

    private val isProcessing = AtomicBoolean(false)
    private var frameCounter = 0L
    private var fpsCount = 0
    private var fpsWindowStart = System.currentTimeMillis()
    private var currentFps = 0

    private var smoothedPose: MutableList<Pt>? = null
    private var smoothedPose3d: MutableList<Pt3>? = null
    private var poseMissCount = 0
    private var smoothedFace: FacePoints? = null
    private var faceEulerY: Float? = null
    private var faceMissCount = 0
    private var lastYaw = YawValues()
    private var latestMaskBitmap: Bitmap? = null
    private var maskPixels: IntArray = IntArray(0)

    private data class YawValues(
        val dx: Float = 1f,
        val dz: Float = 0f,
        val hdx: Float = 1f,
        val hdz: Float = 0f,
        val hv: Float = 0f,
        val valid: Boolean = false,
    )

    fun reset() {
        smoothedPose = null
        smoothedPose3d = null
        poseMissCount = 0
        smoothedFace = null
        faceEulerY = null
        faceMissCount = 0
        lastYaw = YawValues()
        latestMaskBitmap = null
    }

    fun close() {
        runCatching { poseDetector.close() }
        runCatching { faceDetector.close() }
        runCatching { segmenter.close() }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isProcessing.set(false)
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val uprightW = if (rotation % 180 == 0) imageProxy.width else imageProxy.height
        val uprightH = if (rotation % 180 == 0) imageProxy.height else imageProxy.width
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        val step = frameCounter++
        val runFace = (step % 2L == 0L)
        val runSeg = segmentationEnabled && (step % 2L == 1L)

        val poseTask = poseDetector.process(inputImage)
        val faceTask: Task<List<Face>>? = if (runFace) faceDetector.process(inputImage) else null
        val segTask: Task<SegmentationMask>? = if (runSeg) segmenter.process(inputImage) else null

        val tasks = mutableListOf<Task<*>>(poseTask)
        if (faceTask != null) tasks.add(faceTask)
        if (segTask != null) tasks.add(segTask)

        Tasks.whenAllComplete(tasks)
            .addOnCompleteListener {
                try {
                    if (poseTask.isSuccessful) {
                        handlePose(poseTask.result, uprightW.toFloat(), uprightH.toFloat())
                    } else if (++poseMissCount > 6) {
                        smoothedPose = null
                        smoothedPose3d = null
                        lastYaw = YawValues()
                    }

                    if (faceTask != null) {
                        if (faceTask.isSuccessful) {
                            handleFaces(faceTask.result, uprightW.toFloat(), uprightH.toFloat())
                        } else if (++faceMissCount > 4) {
                            smoothedFace = null
                            faceEulerY = null
                        }
                    }

                    if (segTask != null && segTask.isSuccessful) {
                        segTask.result?.let { updateMaskBitmap(it) }
                    } else if (!segmentationEnabled) {
                        latestMaskBitmap = null
                    }

                    val now = System.currentTimeMillis()
                    fpsCount++
                    if (now - fpsWindowStart >= 1500L) {
                        currentFps = ((fpsCount * 1000f) / (now - fpsWindowStart)).toInt()
                        fpsCount = 0
                        fpsWindowStart = now
                    }

                    val poseSnap = smoothedPose?.map { it.copy() } ?: emptyList()
                    val pose3dSnap = smoothedPose3d?.map { it.copy() } ?: emptyList()
                    val hasHands = poseSnap.size > 22 &&
                        (poseSnap[15].v >= 0.45f || poseSnap[16].v >= 0.45f) &&
                        (poseSnap[19].v >= 0.35f || poseSnap[20].v >= 0.35f)

                    onFrame(
                        TrackingFrame(
                            poseNorm = poseSnap,
                            pose3d = pose3dSnap,
                            faceNorm = smoothedFace,
                            yawDx = lastYaw.dx,
                            yawDz = lastYaw.dz,
                            hipYawDx = lastYaw.hdx,
                            hipYawDz = lastYaw.hdz,
                            hipVisibility = lastYaw.hv,
                            has3dYaw = lastYaw.valid,
                            faceEulerY = faceEulerY,
                            imageWidth = uprightW.coerceAtLeast(1),
                            imageHeight = uprightH.coerceAtLeast(1),
                            segMaskBitmap = if (segmentationEnabled) latestMaskBitmap else null,
                            hasPose = poseSnap.isNotEmpty(),
                            hasFace = smoothedFace != null,
                            hasHands = hasHands,
                            fps = currentFps,
                            timestampMs = now,
                        )
                    )
                } finally {
                    isProcessing.set(false)
                    imageProxy.close()
                }
            }
    }

    private fun handlePose(pose: Pose, w: Float, h: Float) {
        val all = pose.allPoseLandmarks
        if (all.isEmpty()) {
            if (++poseMissCount > 6) {
                smoothedPose = null
                smoothedPose3d = null
                lastYaw = YawValues()
            }
            return
        }
        poseMissCount = 0

        val raw = MutableList(33) { Pt(0f, 0f, 0f) }
        val raw3d = MutableList(33) { Pt3(0f, 0f, 0f, 0f) }
        for (lm in all) {
            val idx = lm.landmarkType
            if (idx in 0 until 33) {
                raw[idx] = Pt(
                    x = lm.position.x / w,
                    y = lm.position.y / h,
                    v = lm.inFrameLikelihood,
                )
                val p3 = lm.position3D
                raw3d[idx] = Pt3(
                    x = p3.x,
                    y = p3.y,
                    z = p3.z,
                    v = lm.inFrameLikelihood,
                )
            }
        }

        val current = smoothedPose
        if (current == null || current.size != 33) {
            smoothedPose = raw
        } else {
            val k = 0.6f
            for (i in 0 until 33) {
                val c = current[i]
                val r = raw[i]
                if (c.v < 0.05f) {
                    current[i] = r
                } else {
                    current[i] = Pt(
                        x = c.x + (r.x - c.x) * k,
                        y = c.y + (r.y - c.y) * k,
                        v = r.v,
                    )
                }
            }
        }

        val cur3d = smoothedPose3d
        if (cur3d == null || cur3d.size != 33) {
            smoothedPose3d = raw3d
        } else {
            val k3 = 0.55f
            for (i in 0 until 33) {
                val c = cur3d[i]
                val r = raw3d[i]
                if (c.v < 0.05f) {
                    cur3d[i] = r
                } else {
                    cur3d[i] = Pt3(
                        x = c.x + (r.x - c.x) * k3,
                        y = c.y + (r.y - c.y) * k3,
                        z = c.z + (r.z - c.z) * k3,
                        v = r.v,
                    )
                }
            }
        }

        val lSh = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rSh = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val lHp = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rHp = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        if (lSh != null && rSh != null && lHp != null && rHp != null) {
            val l3 = lSh.position3D
            val r3 = rSh.position3D
            val lh3 = lHp.position3D
            val rh3 = rHp.position3D
            lastYaw = YawValues(
                dx = l3.x - r3.x,
                dz = l3.z - r3.z,
                hdx = lh3.x - rh3.x,
                hdz = lh3.z - rh3.z,
                hv = minOf(lHp.inFrameLikelihood, rHp.inFrameLikelihood),
                valid = true,
            )
        } else {
            lastYaw = YawValues()
        }
    }

    private fun handleFaces(faces: List<Face>, w: Float, h: Float) {
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        if (face == null) {
            if (++faceMissCount > 4) {
                smoothedFace = null
                faceEulerY = null
            }
            return
        }
        faceMissCount = 0
        faceEulerY = face.headEulerAngleY

        fun norm(p: PointF): Pt = Pt(p.x / w, p.y / h, 1f)
        fun contourCenter(type: Int): Pt? {
            val pts = face.getContour(type)?.points ?: return null
            if (pts.isEmpty()) return null
            var sx = 0f
            var sy = 0f
            for (pt in pts) {
                sx += pt.x
                sy += pt.y
            }
            return Pt((sx / pts.size) / w, (sy / pts.size) / h, 1f)
        }

        val box = face.boundingBox
        val oval = face.getContour(FaceContour.FACE)?.points
        val topPt = oval?.firstOrNull()?.let { norm(it) }
            ?: Pt(box.centerX() / w, box.top / h, 1f)
        val chinPt = oval?.getOrNull(oval.size / 2)?.let { norm(it) }
            ?: Pt(box.centerX() / w, box.bottom / h, 1f)
        val leftJawPt = oval?.getOrNull(oval.size * 3 / 8)?.let { norm(it) }
            ?: Pt(box.right / w, (box.top + box.height() * 0.72f) / h, 1f)
        val rightJawPt = oval?.getOrNull(oval.size * 5 / 8)?.let { norm(it) }
            ?: Pt(box.left / w, (box.top + box.height() * 0.72f) / h, 1f)

        val lEye = contourCenter(FaceContour.LEFT_EYE)
            ?: face.getLandmark(FaceLandmark.LEFT_EYE)?.position?.let { norm(it) }
            ?: Pt((box.left + box.width() * 0.67f) / w, (box.top + box.height() * 0.38f) / h, 1f)
        val rEye = contourCenter(FaceContour.RIGHT_EYE)
            ?: face.getLandmark(FaceLandmark.RIGHT_EYE)?.position?.let { norm(it) }
            ?: Pt((box.left + box.width() * 0.33f) / w, (box.top + box.height() * 0.38f) / h, 1f)

        val nosePts = face.getContour(FaceContour.NOSE_BRIDGE)?.points
        val noseBridge = nosePts?.firstOrNull()?.let { norm(it) }
            ?: Pt((lEye.x + rEye.x) / 2f, (lEye.y + rEye.y) / 2f, 1f)
        val noseTip = nosePts?.lastOrNull()?.let { norm(it) }
            ?: face.getLandmark(FaceLandmark.NOSE_BASE)?.position?.let { norm(it) }
            ?: Pt(box.centerX() / w, (box.top + box.height() * 0.56f) / h, 1f)

        val lCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)?.position?.let { norm(it) }
            ?: Pt((box.left + box.width() * 0.72f) / w, (box.top + box.height() * 0.58f) / h, 1f)
        val rCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)?.position?.let { norm(it) }
            ?: Pt((box.left + box.width() * 0.28f) / w, (box.top + box.height() * 0.58f) / h, 1f)

        val mLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position?.let { norm(it) }
            ?: Pt((box.left + box.width() * 0.62f) / w, (box.top + box.height() * 0.74f) / h, 1f)
        val mRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position?.let { norm(it) }
            ?: Pt((box.left + box.width() * 0.38f) / w, (box.top + box.height() * 0.74f) / h, 1f)
        val mCenter = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position?.let { norm(it) }
            ?: Pt((mLeft.x + mRight.x) / 2f, (mLeft.y + mRight.y) / 2f, 1f)

        val center = Pt((topPt.x + chinPt.x) / 2f, (topPt.y + chinPt.y) / 2f, 1f)
        val height = hypot(topPt.x - chinPt.x, topPt.y - chinPt.y)
        val tilt = atan2(lEye.y - rEye.y, lEye.x - rEye.x)
        val forehead = Pt(
            x = topPt.x + (center.x - topPt.x) * 0.25f,
            y = topPt.y + (center.y - topPt.y) * 0.25f,
            v = 1f,
        )

        val next = FacePoints(
            center = center,
            height = height,
            tiltRad = tilt,
            forehead = forehead,
            leftEye = lEye,
            rightEye = rEye,
            noseBridge = noseBridge,
            noseTip = noseTip,
            leftCheek = lCheek,
            rightCheek = rCheek,
            mouthLeft = mLeft,
            mouthRight = mRight,
            mouthCenter = mCenter,
            chin = chinPt,
            leftJaw = leftJawPt,
            rightJaw = rightJawPt,
        )
        smoothedFace = easeFace(smoothedFace, next, 0.7f)
    }

    private fun easeFace(prev: FacePoints?, next: FacePoints, k: Float): FacePoints {
        if (prev == null) return next
        fun lp(a: Pt, b: Pt): Pt = Pt(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, b.v)
        return FacePoints(
            center = lp(prev.center, next.center),
            height = prev.height + (next.height - prev.height) * k,
            tiltRad = prev.tiltRad + (next.tiltRad - prev.tiltRad) * k,
            forehead = lp(prev.forehead, next.forehead),
            leftEye = lp(prev.leftEye, next.leftEye),
            rightEye = lp(prev.rightEye, next.rightEye),
            noseBridge = lp(prev.noseBridge, next.noseBridge),
            noseTip = lp(prev.noseTip, next.noseTip),
            leftCheek = lp(prev.leftCheek, next.leftCheek),
            rightCheek = lp(prev.rightCheek, next.rightCheek),
            mouthLeft = lp(prev.mouthLeft, next.mouthLeft),
            mouthRight = lp(prev.mouthRight, next.mouthRight),
            mouthCenter = lp(prev.mouthCenter, next.mouthCenter),
            chin = lp(prev.chin, next.chin),
            leftJaw = lp(prev.leftJaw, next.leftJaw),
            rightJaw = lp(prev.rightJaw, next.rightJaw),
        )
    }

    private fun updateMaskBitmap(mask: SegmentationMask) {
        val mw = mask.width
        val mh = mask.height
        val step = if (mw > 320) 4 else 2
        val sw = (mw / step).coerceAtLeast(1)
        val sh = (mh / step).coerceAtLeast(1)

        val buf = mask.buffer
        buf.rewind()
        val floats = buf.asFloatBuffer()
        val total = sw * sh
        if (maskPixels.size != total) {
            maskPixels = IntArray(total)
        }

        for (y in 0 until sh) {
            val rowOffset = y * step * mw
            for (x in 0 until sw) {
                val conf = floats.get(rowOffset + x * step)
                val v = ((conf - 0.35f) / 0.4f).coerceIn(0f, 1f)
                val alpha = (v * 255f).toInt()
                maskPixels[y * sw + x] = Color.argb(alpha, 255, 255, 255)
            }
        }

        val bmp = latestMaskBitmap?.takeIf { it.width == sw && it.height == sh }
            ?: Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888).also { latestMaskBitmap = it }
        bmp.setPixels(maskPixels, 0, sw, 0, 0, sw, sh)
    }
}
