package com.example.model

import android.graphics.Bitmap

enum class AnatomySystem(val labelAr: String) {
    ALL("كل الأنظمة"),
    BONES("الهيكل العظمي"),
    ORGANS("الأعضاء"),
    MUSCLES("العضلات"),
}

enum class VisualLook(val labelAr: String) {
    XRAY("أشعة"),
    COLORED("نموذج ملوّن"),
}

enum class ViewMode(val labelAr: String) {
    FULL("عرض كامل"),
    LENS("عدسة الأشعة"),
    SCAN("مسح"),
}

enum class BodyViewDirection(val labelAr: String) {
    AUTO("تلقائي"),
    FRONT("أمامي"),
    BACK("خلفي"),
}

enum class StageAspect(val labelAr: String) {
    FULLSCREEN("ملء الشاشة"),
    RATIO_16_9("16:9 (لابتوب)"),
    RATIO_9_16("9:16 (ريلز)"),
}

enum class BodyRegion(val key: String, val labelAr: String) {
    ALL("all", "كامل الجسم"),
    HEAD("head", "الرأس"),
    NECK("neck", "الرقبة"),
    SHOULDERS("shoulders", "الكتفان"),
    CHEST("chest", "الصدر"),
    ABDOMEN("abdomen", "البطن"),
    BACK("back", "الظهر"),
    PELVIS("pelvis", "الحوض"),
    ARMS("arms", "العضدان"),
    FOREARMS("forearms", "الساعدان"),
    HANDS("hands", "اليدان"),
    THIGHS("thighs", "الفخذان"),
    SHINS("shins", "الساقان"),
    FEET("feet", "القدمان"),
}

enum class ZoneKey(val labelAr: String) {
    HEAD("الرأس"),
    NECK("الرقبة"),
    TORSO("الجذع"),
    PELVIS("الحوض"),
    ARM_R("العضد الأيمن"),
    ARM_L("العضد الأيسر"),
    FOREARM_R("الساعد الأيمن"),
    FOREARM_L("الساعد الأيسر"),
    HAND_R("اليد اليمنى"),
    HAND_L("اليد اليسرى"),
    THIGH_R("الفخذ الأيمن"),
    THIGH_L("الفخذ الأيسر"),
    SHIN_R("الساق اليمنى"),
    SHIN_L("الساق اليسرى"),
    FOOT_R("القدم اليمنى"),
    FOOT_L("القدم اليسرى"),
}

data class Pt(
    val x: Float,
    val y: Float,
    val v: Float = 1f,
)

data class Pt3(
    val x: Float,
    val y: Float,
    val z: Float,
    val v: Float = 1f,
)

data class BodyCalibration(
    val rW: Float = 0f,
    val rA: Float = 0f,
    val rF: Float = 0f,
    val rT: Float = 0f,
    val rS: Float = 0f,
) {
    val hasTorso: Boolean get() = rW > 0f
    val hasLimbs: Boolean get() = rA > 0f && rT > 0f
}

data class TorsoFrame(
    val a: Pt,   // right shoulder (pose 12)
    val b: Pt,   // left shoulder (pose 11)
    val c: Pt,   // right hip (pose 24)
    val e: Pt,   // left hip (pose 23)
    val sm: Pt,  // shoulder midpoint
    val hm: Pt,  // hip midpoint
    val length: Float,
    val axisE: Pt, // depth normal unit vector
    val wf: Float, // reference shoulder width
    val cosYaw: Float,
    val sinYaw: Float,
) {
    fun project(aFrac: Float, bFrac: Float, depthFrac: Float = 0f): Pt {
        val x1 = a.x + (b.x - a.x) * aFrac
        val y1 = a.y + (b.y - a.y) * aFrac
        val x2 = c.x + (e.x - c.x) * aFrac
        val y2 = c.y + (e.y - c.y) * aFrac
        val k = depthFrac * wf * sinYaw
        return Pt(
            x = x1 + (x2 - x1) * bFrac + axisE.x * k,
            y = y1 + (y2 - y1) * bFrac + axisE.y * k,
            v = 1f,
        )
    }

    fun projectedWidth(): Float = maxOf(wf * kotlin.math.abs(cosYaw), wf * 0.35f)
}

data class FacePoints(
    val center: Pt,
    val height: Float,
    val tiltRad: Float,
    val forehead: Pt,
    val leftEye: Pt,
    val rightEye: Pt,
    val noseBridge: Pt,
    val noseTip: Pt,
    val leftCheek: Pt,
    val rightCheek: Pt,
    val mouthLeft: Pt,
    val mouthRight: Pt,
    val mouthCenter: Pt,
    val chin: Pt,
    val leftJaw: Pt,
    val rightJaw: Pt,
)

data class AnatomyPart(
    val ar: String,
    val en: String,
    val fact: String,
    val approx: Boolean,
    val group: BodyRegion,
    val priority: Float = 0f,
    val viewConstraint: String? = null, // "f" front, "b" back, null both
    val locator: (RenderContextData) -> Pair<Pt, Float>?,
)

data class VisiblePart(
    val part: AnatomyPart,
    val center: Pt,
    val radius: Float,
)

data class RenderContextData(
    val pose: List<Pt>,
    val face: FacePoints?,
    val headCenter: Pt?,
    val headSize: Float,
    val torso: TorsoFrame?,
    val shoulderWidth: Float,
    val calibration: BodyCalibration?,
) {
    fun pv(index: Int): Pt? = pose.getOrNull(index)?.takeIf { it.v >= 0.45f }
}

data class ZoneState(
    val on: Boolean = false,
    val alpha: Float = 0f,
)

data class CalibrationSession(
    val stage: Int = 0, // 0=idle, 1=countdown, 2=sampling
    val targetTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val rW: MutableList<Float> = mutableListOf(),
    val rA: MutableList<Float> = mutableListOf(),
    val rF: MutableList<Float> = mutableListOf(),
    val rT: MutableList<Float> = mutableListOf(),
    val rS: MutableList<Float> = mutableListOf(),
)

data class TrackingFrame(
    val poseNorm: List<Pt> = emptyList(),
    val pose3d: List<Pt3> = emptyList(),
    val faceNorm: FacePoints? = null,
    val yawDx: Float = 1f,
    val yawDz: Float = 0f,
    val hipYawDx: Float = 1f,
    val hipYawDz: Float = 0f,
    val hipVisibility: Float = 0f,
    val has3dYaw: Boolean = false,
    val faceEulerY: Float? = null,
    val imageWidth: Int = 1,
    val imageHeight: Int = 1,
    val segMaskBitmap: Bitmap? = null,
    val hasPose: Boolean = false,
    val hasFace: Boolean = false,
    val hasHands: Boolean = false,
    val fps: Int = 0,
    val timestampMs: Long = 0L,
)
