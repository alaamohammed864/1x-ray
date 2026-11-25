package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GlbModelRepository
import com.example.data.GlbNodeLabelMapper
import com.example.data.GlbSourceType
import com.example.model.AnatomySystem
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("عدسة التشريح", appName)
  }

  @Test
  fun `all seven bundled glb models load offline and parse cleanly`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val models = GlbModelRepository.listInstalledModels(context)

    val expectedNames = mapOf(
      "dolasim.glb" to "الجهاز الدوري",
      "eklem.glb" to "المفاصل والأربطة",
      "ic-organlar.glb" to "الأعضاء الداخلية",
      "iskelet.glb" to "الهيكل العظمي",
      "kas.glb" to "الجهاز العضلي",
      "lenf.glb" to "الجهاز اللمفاوي",
      "sinir.glb" to "الجهاز العصبي",
    )

    val bundled = models.filter { it.sourceType == GlbSourceType.BUNDLED_ASSET }
    assertEquals(expectedNames.keys.sorted(), bundled.map { it.fileName })

    for (model in bundled) {
      assertEquals(expectedNames[model.fileName], model.displayName)
      val bytes = GlbModelRepository.loadModelBytes(context, model)
      assertTrue("Failed reading ${model.fileName}", bytes != null && GlbModelRepository.isValidGlbBytes(bytes))

      val header = ByteBuffer.wrap(bytes!!, 0, 20).order(ByteOrder.LITTLE_ENDIAN)
      val magic = header.int
      val version = header.int
      val totalLength = header.int
      val jsonChunkLength = header.int
      val jsonChunkType = header.int

      assertEquals(0x46546C67, magic) // "glTF"
      assertEquals(2, version)
      assertEquals(bytes.size, totalLength)
      assertEquals(0x4E4F534A, jsonChunkType) // "JSON"
      assertTrue(jsonChunkLength > 0 && 20 + jsonChunkLength <= bytes.size)

      val jsonText = String(bytes, 20, jsonChunkLength, Charsets.UTF_8).trimEnd('\u0000', ' ')
      val gltf = JSONObject(jsonText)
      val nodes = gltf.optJSONArray("nodes")
      val meshes = gltf.optJSONArray("meshes")
      val materials = gltf.optJSONArray("materials")
      val animations = gltf.optJSONArray("animations")
      val extensionsUsed = gltf.optJSONArray("extensionsUsed")

      assertTrue("${model.fileName} should have meshes", (meshes?.length() ?: 0) > 0)
      assertTrue("${model.fileName} should have nodes", (nodes?.length() ?: 0) > 0)

      val unmapped = mutableListOf<String>()
      var mappedCount = 0
      if (nodes != null) {
        for (i in 0 until nodes.length()) {
          val nodeName = nodes.getJSONObject(i).optString("name", "")
          val mapped = GlbNodeLabelMapper.mapNode(i + 1, nodeName)
          if (mapped != null) {
            if (mapped.system == AnatomySystem.ALL) {
              unmapped.add(nodeName)
            } else {
              mappedCount++
            }
          }
        }
      }

      val extList = buildList {
        if (extensionsUsed != null) {
          for (i in 0 until extensionsUsed.length()) add(extensionsUsed.getString(i))
        }
      }
      assertFalse(
        "${model.fileName} uses unsupported Draco extension without decoder check",
        extList.contains("KHR_draco_mesh_compression")
      )

      println(
        "GLB_AUDIT ${model.fileName}: size=${bytes.size}, nodes=${nodes?.length() ?: 0}, " +
          "meshes=${meshes?.length() ?: 0}, materials=${materials?.length() ?: 0}, " +
          "animations=${animations?.length() ?: 0}, mapped=$mappedCount, " +
          "unmappedCount=${unmapped.size}, sampleUnmapped=${unmapped.take(15)}"
      )
      val totalChecked = mappedCount + unmapped.size
      if (totalChecked > 0) {
        val ratio = mappedCount.toFloat() / totalChecked.toFloat()
        assertTrue(
          "${model.fileName} mapping ratio too low ($mappedCount/$totalChecked): ${unmapped.take(10)}",
          ratio >= 0.80f
        )
      }
    }
  }
}
