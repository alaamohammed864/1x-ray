package com.example

import com.example.data.AnatomyCatalog
import com.example.model.BodyRegion
import com.example.model.Pt
import com.example.model.RenderContextData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnatomyCatalogTest {

    @Test
    fun `arabic search normalization strips definite article and diacritics`() {
        assertEquals("ركبه", AnatomyCatalog.normalizeArabic("الرُّكْبَة"))
        assertEquals("كبد", AnatomyCatalog.normalizeArabic("الكبد"))
        assertEquals("امعاء", AnatomyCatalog.normalizeArabic("الأمعاء"))
    }

    @Test
    fun `catalog contains rich anatomical parts across all major regions`() {
        assertTrue(AnatomyCatalog.parts.size >= 50)
        assertTrue(AnatomyCatalog.parts.any { it.group == BodyRegion.HEAD && it.ar == "الجمجمة" })
        assertTrue(AnatomyCatalog.parts.any { it.group == BodyRegion.CHEST && it.ar == "القلب" })
        assertTrue(AnatomyCatalog.parts.any { it.group == BodyRegion.ABDOMEN && it.ar == "الكبد" })
        assertTrue(AnatomyCatalog.parts.any { it.group == BodyRegion.BACK && it.ar == "العمود الفقري" })
    }

    @Test
    fun `knee locator resolves position when pose knee landmark is visible`() {
        val pose = MutableList(33) { Pt(0f, 0f, 0f) }
        pose[12] = Pt(300f, 200f, 0.9f)
        pose[11] = Pt(500f, 200f, 0.9f)
        pose[26] = Pt(340f, 700f, 0.95f)
        val ctx = RenderContextData(
            pose = pose,
            face = null,
            headCenter = null,
            headSize = 0f,
            torso = null,
            shoulderWidth = 200f,
            calibration = null,
        )
        val rightKnee = AnatomyCatalog.parts.first { it.ar == "الركبة اليمنى" }
        val result = rightKnee.locator(ctx)
        assertNotNull(result)
        assertEquals(340f, result!!.first.x, 0.01f)
        assertEquals(700f, result.first.y, 0.01f)
    }

    @Test
    fun `pose3d kinematics builds live articulated skeleton and follows raised arm`() {
        val pose3d = MutableList(33) { com.example.model.Pt3(0f, 0f, 0f, 0.9f) }
        // Hips (23 left, 24 right) at y=100; Shoulders (11 left, 12 right) at y=0 (Y down)
        pose3d[11] = com.example.model.Pt3(100f, 0f, 0f, 0.95f)
        pose3d[12] = com.example.model.Pt3(-100f, 0f, 0f, 0.95f)
        pose3d[23] = com.example.model.Pt3(60f, 200f, 0f, 0.95f)
        pose3d[24] = com.example.model.Pt3(-60f, 200f, 0f, 0.95f)
        // Raised right wrist (16) above shoulder
        pose3d[14] = com.example.model.Pt3(-140f, -60f, -20f, 0.95f)
        pose3d[16] = com.example.model.Pt3(-160f, -140f, -30f, 0.95f)

        val articulated = com.example.render.Pose3dKinematics.buildArticulatedPose(
            pose3d = pose3d,
            isFrontCamera = true,
            faceEulerYDeg = 10f,
        )
        assertTrue(articulated.isLiveTracked)
        assertTrue(articulated.hasArms)
        // Raised right wrist in Filament Y-up world must be higher than right shoulder
        assertTrue(articulated.wrR.y > articulated.shR.y)
        // In front-camera mirror mode, negative raw X (-160) maps to positive mirror +X
        assertTrue(articulated.wrR.x > 0f)
    }

    @Test
    fun `glb header validation and arabic node label mapping work accurately`() {
        val validGlbHeader = byteArrayOf(0x67, 0x6C, 0x54, 0x46, 2, 0, 0, 0, 12, 0, 0, 0)
        val invalidHeader = byteArrayOf(0x7B, 0x22, 0x61, 0x73, 0, 0, 0, 0, 0, 0, 0, 0)
        assertTrue(com.example.data.GlbModelRepository.isValidGlbBytes(validGlbHeader))
        assertTrue(!com.example.data.GlbModelRepository.isValidGlbBytes(invalidHeader))

        val heartNode = com.example.data.GlbNodeLabelMapper.mapNode(1, "VH_M_Heart_ventricle")
        assertNotNull(heartNode)
        assertEquals(com.example.model.AnatomySystem.ORGANS, heartNode!!.system)

        val boneNode = com.example.data.GlbNodeLabelMapper.mapNode(2, "Left_Femur_bone")
        assertNotNull(boneNode)
        assertEquals(com.example.model.AnatomySystem.BONES, boneNode!!.system)

        assertTrue(com.example.data.AnatomyAttributions.entries.any { it.source.contains("biv-me") && it.citationOrUrl.contains("Dillon et al. 2026") })
    }
}
