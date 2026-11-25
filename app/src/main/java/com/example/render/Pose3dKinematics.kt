package com.example.render

import com.example.model.Pt3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun len(): Float = sqrt(x * x + y * y + z * z)
    fun normalized(): Vec3 {
        val l = len()
        return if (l > 1e-5f) Vec3(x / l, y / l, z / l) else Vec3(0f, 1f, 0f)
    }
    fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )
}

data class ArticulatedPose3d(
    val isLiveTracked: Boolean,
    val torsoMatrix: FloatArray,
    val headMatrix: FloatArray,
    val shR: Vec3,
    val shL: Vec3,
    val elR: Vec3,
    val elL: Vec3,
    val wrR: Vec3,
    val wrL: Vec3,
    val hdR: Vec3,
    val hdL: Vec3,
    val hpR: Vec3,
    val hpL: Vec3,
    val knR: Vec3,
    val knL: Vec3,
    val akR: Vec3,
    val akL: Vec3,
    val ftR: Vec3,
    val ftL: Vec3,
    val hasArms: Boolean,
    val hasLegs: Boolean,
)

object Pose3dKinematics {

    private const val TARGET_TORSO_LEN = 0.85f
    private const val HIP_CENTER_Y = -0.18f

    fun buildArticulatedPose(
        pose3d: List<Pt3>,
        isFrontCamera: Boolean,
        faceEulerYDeg: Float?,
    ): ArticulatedPose3d {
        if (pose3d.size < 33) return defaultStandingPose()

        val lShRaw = pose3d[11]
        val rShRaw = pose3d[12]
        val lHpRaw = pose3d[23]
        val rHpRaw = pose3d[24]
        if (lShRaw.v < 0.35f || rShRaw.v < 0.35f || lHpRaw.v < 0.35f || rHpRaw.v < 0.35f) {
            return defaultStandingPose()
        }

        val hipMidRaw = Vec3(
            (lHpRaw.x + rHpRaw.x) * 0.5f,
            (lHpRaw.y + rHpRaw.y) * 0.5f,
            (lHpRaw.z + rHpRaw.z) * 0.5f,
        )
        val shMidRaw = Vec3(
            (lShRaw.x + rShRaw.x) * 0.5f,
            (lShRaw.y + rShRaw.y) * 0.5f,
            (lShRaw.z + rShRaw.z) * 0.5f,
        )
        val rawTorsoLen = (shMidRaw - hipMidRaw).len().coerceAtLeast(1e-3f)
        val scale = TARGET_TORSO_LEN / rawTorsoLen

        fun mapPt(idx: Int): Vec3 {
            val p = pose3d[idx]
            val dx = (p.x - hipMidRaw.x) * scale
            val dy = -(p.y - hipMidRaw.y) * scale + HIP_CENTER_Y
            val dz = -(p.z - hipMidRaw.z) * scale * 0.55f
            return Vec3(
                x = if (isFrontCamera) -dx else dx,
                y = dy,
                z = dz,
            )
        }

        val shL = mapPt(11)
        val shR = mapPt(12)
        val hpL = mapPt(23)
        val hpR = mapPt(24)

        val hipMid = (hpL + hpR) * 0.5f
        val shMid = (shL + shR) * 0.5f

        val up = (shMid - hipMid).normalized()
        val rawRight = if (isFrontCamera) (shR - shL) else (shL - shR)
        val forward = rawRight.cross(up).let {
            if (it.len() > 1e-4f) it.normalized() else Vec3(0f, 0f, 1f)
        }
        val right = up.cross(forward).normalized()

        val torsoM = basisMatrix(right, up, forward, hipMid)

        // Head transform
        val nose = pose3d[0]
        val earL = pose3d[7]
        val earR = pose3d[8]
        val headCenter = when {
            earL.v >= 0.35f && earR.v >= 0.35f -> (mapPt(7) + mapPt(8)) * 0.5f + up * 0.04f
            nose.v >= 0.35f -> mapPt(0) + Vec3(0f, 0.05f, -0.06f)
            else -> shMid + up * 0.28f
        }
        val headYawRad = ((faceEulerYDeg ?: 0f) * (if (isFrontCamera) 1f else -1f)) * Math.PI.toFloat() / 180f * 0.35f
        val baseHeadM = basisMatrix(right, up, forward, headCenter)
        val headM = rotateLocalY(baseHeadM, headYawRad)

        // Arms (visible if elbows/wrists have reasonable likelihood)
        val elR = if (pose3d[14].v >= 0.35f) mapPt(14) else shR + right * 0.14f - up * 0.34f
        val elL = if (pose3d[13].v >= 0.35f) mapPt(13) else shL - right * 0.14f - up * 0.34f
        val wrR = if (pose3d[16].v >= 0.35f) mapPt(16) else elR + right * 0.06f - up * 0.32f
        val wrL = if (pose3d[15].v >= 0.35f) mapPt(15) else elL - right * 0.06f - up * 0.32f
        val hdR = if (pose3d[20].v >= 0.3f) mapPt(20) else wrR - up * 0.14f
        val hdL = if (pose3d[19].v >= 0.3f) mapPt(19) else wrL - up * 0.14f

        // Legs (visible if knees/ankles detected; fallback to natural standing legs below hips)
        val hasLegs = pose3d[25].v >= 0.35f || pose3d[26].v >= 0.35f
        val knR = if (pose3d[26].v >= 0.35f) mapPt(26) else hpR - up * 0.52f
        val knL = if (pose3d[25].v >= 0.35f) mapPt(25) else hpL - up * 0.52f
        val akR = if (pose3d[28].v >= 0.35f) mapPt(28) else knR - up * 0.50f
        val akL = if (pose3d[27].v >= 0.35f) mapPt(27) else knL - up * 0.50f
        val ftR = if (pose3d[32].v >= 0.3f) mapPt(32) else akR + forward * 0.18f - up * 0.06f
        val ftL = if (pose3d[31].v >= 0.3f) mapPt(31) else akL + forward * 0.18f - up * 0.06f

        return ArticulatedPose3d(
            isLiveTracked = true,
            torsoMatrix = torsoM,
            headMatrix = headM,
            shR = shR,
            shL = shL,
            elR = elR,
            elL = elL,
            wrR = wrR,
            wrL = wrL,
            hdR = hdR,
            hdL = hdL,
            hpR = hpR,
            hpL = hpL,
            knR = knR,
            knL = knL,
            akR = akR,
            akL = akL,
            ftR = ftR,
            ftL = ftL,
            hasArms = pose3d[13].v >= 0.35f || pose3d[14].v >= 0.35f,
            hasLegs = hasLegs,
        )
    }

    fun defaultStandingPose(): ArticulatedPose3d {
        val torsoM = translationMatrix(0f, HIP_CENTER_Y, 0f)
        val headM = translationMatrix(0f, HIP_CENTER_Y + TARGET_TORSO_LEN + 0.27f, 0.02f)
        val sy = HIP_CENTER_Y + TARGET_TORSO_LEN * 0.94f
        val hy = HIP_CENTER_Y
        return ArticulatedPose3d(
            isLiveTracked = false,
            torsoMatrix = torsoM,
            headMatrix = headM,
            shR = Vec3(0.28f, sy, 0f),
            shL = Vec3(-0.28f, sy, 0f),
            elR = Vec3(0.37f, sy - 0.35f, 0.03f),
            elL = Vec3(-0.37f, sy - 0.35f, 0.03f),
            wrR = Vec3(0.41f, sy - 0.68f, 0.08f),
            wrL = Vec3(-0.41f, sy - 0.68f, 0.08f),
            hdR = Vec3(0.43f, sy - 0.82f, 0.10f),
            hdL = Vec3(-0.43f, sy - 0.82f, 0.10f),
            hpR = Vec3(0.15f, hy, 0f),
            hpL = Vec3(-0.15f, hy, 0f),
            knR = Vec3(0.16f, hy - 0.50f, 0.02f),
            knL = Vec3(-0.16f, hy - 0.50f, 0.02f),
            akR = Vec3(0.16f, hy - 0.98f, 0f),
            akL = Vec3(-0.16f, hy - 0.98f, 0f),
            ftR = Vec3(0.17f, hy - 1.04f, 0.17f),
            ftL = Vec3(-0.17f, hy - 1.04f, 0.17f),
            hasArms = true,
            hasLegs = true,
        )
    }

    fun segmentMatrix(a: Vec3, b: Vec3, thickness: Float): FloatArray {
        val mid = (a + b) * 0.5f
        val diff = b - a
        val len = diff.len().coerceAtLeast(0.02f)
        val up = diff.normalized()
        val ref = if (abs(up.z) < 0.9f) Vec3(0f, 0f, 1f) else Vec3(1f, 0f, 0f)
        val right = up.cross(ref).normalized()
        val forward = right.cross(up).normalized()

        return floatArrayOf(
            right.x * thickness, right.y * thickness, right.z * thickness, 0f,
            up.x * len, up.y * len, up.z * len, 0f,
            forward.x * thickness, forward.y * thickness, forward.z * thickness, 0f,
            mid.x, mid.y, mid.z, 1f,
        )
    }

    fun jointMatrix(p: Vec3, radius: Float): FloatArray {
        return floatArrayOf(
            radius, 0f, 0f, 0f,
            0f, radius, 0f, 0f,
            0f, 0f, radius, 0f,
            p.x, p.y, p.z, 1f,
        )
    }

    private fun basisMatrix(right: Vec3, up: Vec3, forward: Vec3, origin: Vec3): FloatArray {
        return floatArrayOf(
            right.x, right.y, right.z, 0f,
            up.x, up.y, up.z, 0f,
            forward.x, forward.y, forward.z, 0f,
            origin.x, origin.y, origin.z, 1f,
        )
    }

    private fun translationMatrix(tx: Float, ty: Float, tz: Float): FloatArray {
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            tx, ty, tz, 1f,
        )
    }

    private fun rotateLocalY(m: FloatArray, rad: Float): FloatArray {
        val c = cos(rad)
        val s = sin(rad)
        val out = m.copyOf()
        // Column 0' = col0 * c - col2 * s ; Column 2' = col0 * s + col2 * c
        for (row in 0..3) {
            val c0 = m[row]
            val c2 = m[8 + row]
            out[row] = c0 * c - c2 * s
            out[8 + row] = c0 * s + c2 * c
        }
        return out
    }
}
