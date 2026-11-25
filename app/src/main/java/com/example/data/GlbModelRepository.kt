package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

enum class GlbSourceType(val labelAr: String) {
    BUNDLED_ASSET("مدمج في التطبيق"),
    LOCAL_IMPORTED("مستورد محليًا (دون اتصال)"),
}

data class InstalledGlbModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val sourceType: GlbSourceType,
    val pathOrAsset: String,
) {
    val formattedSize: String
        get() = when {
            sizeBytes >= 1024L * 1024L -> String.format("%.2f MB", sizeBytes / (1024f * 1024f))
            sizeBytes >= 1024L -> "${sizeBytes / 1024L} KB"
            else -> "$sizeBytes B"
        }
}

sealed interface GlbImportResult {
    data class Success(val model: InstalledGlbModel) : GlbImportResult
    data class Error(val message: String) : GlbImportResult
}

object GlbModelRepository {

    private const val MODELS_SUBDIR = "models"
    private val GLB_MAGIC = byteArrayOf(0x67, 0x6C, 0x54, 0x46) // "glTF"

    fun getLocalModelsDir(context: Context): File =
        File(context.filesDir, MODELS_SUBDIR).apply { mkdirs() }

    suspend fun listInstalledModels(context: Context): List<InstalledGlbModel> = withContext(Dispatchers.IO) {
        val result = mutableListOf<InstalledGlbModel>()

        // 1. Probe bundled assets/models/*.glb
        runCatching {
            val assetNames = context.assets.list(MODELS_SUBDIR)?.filter {
                it.endsWith(".glb", ignoreCase = true)
            }?.sorted() ?: emptyList()
            for (name in assetNames) {
                val assetPath = "$MODELS_SUBDIR/$name"
                val size = runCatching {
                    context.assets.openFd(assetPath).use { it.length }
                }.getOrElse {
                    context.assets.open(assetPath).use { it.available().toLong() }
                }
                result.add(
                    InstalledGlbModel(
                        id = "asset:$name",
                        displayName = cleanDisplayName(name),
                        fileName = name,
                        sizeBytes = size,
                        sourceType = GlbSourceType.BUNDLED_ASSET,
                        pathOrAsset = assetPath,
                    )
                )
            }
        }

        // 2. Probe offline user-imported models in filesDir/models/*.glb
        val localDir = getLocalModelsDir(context)
        localDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".glb", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.forEach { file ->
                result.add(
                    InstalledGlbModel(
                        id = "local:${file.name}",
                        displayName = cleanDisplayName(file.name),
                        fileName = file.name,
                        sizeBytes = file.length(),
                        sourceType = GlbSourceType.LOCAL_IMPORTED,
                        pathOrAsset = file.absolutePath,
                    )
                )
            }

        result
    }

    suspend fun importGlbFromUri(context: Context, uri: Uri): GlbImportResult = withContext(Dispatchers.IO) {
        try {
            val rawName = queryDisplayName(context, uri) ?: "model_${System.currentTimeMillis()}.glb"
            val sanitized = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val finalName = if (sanitized.endsWith(".glb", ignoreCase = true)) sanitized else "$sanitized.glb"

            val bytes = context.contentResolver.openInputStream(uri)?.use { readAllBytes(it) }
                ?: return@withContext GlbImportResult.Error("تعذّر فتح الملف المحدد.")

            if (!isValidGlbBytes(bytes)) {
                return@withContext GlbImportResult.Error("الملف ليس بصيغة GLB الثنائية الصالحة (glTF 2.0).")
            }

            val destFile = File(getLocalModelsDir(context), finalName)
            destFile.writeBytes(bytes)

            val model = InstalledGlbModel(
                id = "local:$finalName",
                displayName = cleanDisplayName(finalName),
                fileName = finalName,
                sizeBytes = destFile.length(),
                sourceType = GlbSourceType.LOCAL_IMPORTED,
                pathOrAsset = destFile.absolutePath,
            )
            GlbImportResult.Success(model)
        } catch (e: Exception) {
            GlbImportResult.Error("تعذّر استيراد النموذج: ${e.localizedMessage ?: "خطأ غير متوقع"}")
        }
    }

    suspend fun loadModelBytes(context: Context, model: InstalledGlbModel): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            when (model.sourceType) {
                GlbSourceType.BUNDLED_ASSET -> context.assets.open(model.pathOrAsset).use { readAllBytes(it) }
                GlbSourceType.LOCAL_IMPORTED -> File(model.pathOrAsset).takeIf { it.exists() }?.readBytes()
            }
        }.getOrNull()
    }

    suspend fun deleteImportedModel(model: InstalledGlbModel): Boolean = withContext(Dispatchers.IO) {
        if (model.sourceType != GlbSourceType.LOCAL_IMPORTED) return@withContext false
        runCatching { File(model.pathOrAsset).delete() }.getOrDefault(false)
    }

    fun isValidGlbBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        return bytes[0] == GLB_MAGIC[0] &&
            bytes[1] == GLB_MAGIC[1] &&
            bytes[2] == GLB_MAGIC[2] &&
            bytes[3] == GLB_MAGIC[3]
    }

    private fun cleanDisplayName(fileName: String): String {
        val stem = fileName.substringBeforeLast('.')
        return when {
            stem.contains("heart", true) || stem.contains("kalp", true) || stem.contains("biv", true) -> "القلب ($stem)"
            stem.contains("brain", true) || stem.contains("beyin", true) || stem.contains("sinir", true) || stem.contains("nerv", true) -> "الجهاز العصبي والدماغ ($stem)"
            stem.contains("lenf", true) || stem.contains("lymph", true) -> "الجهاز اللمفاوي ($stem)"
            stem.contains("lung", true) || stem.contains("akciger", true) || stem.contains("solunum", true) || stem.contains("respir", true) -> "الجهاز التنفسي ($stem)"
            stem.contains("liver", true) || stem.contains("karaciger", true) -> "الكبد ($stem)"
            stem.contains("kidney", true) || stem.contains("bobrek", true) || stem.contains("bosaltim", true) || stem.contains("urin", true) -> "الجهاز البولي والكلى ($stem)"
            stem.contains("sindirim", true) || stem.contains("digest", true) || stem.contains("mide", true) || stem.contains("stomach", true) -> "الجهاز الهضمي ($stem)"
            stem.contains("ureme", true) || stem.contains("reprod", true) -> "الجهاز التناسلي ($stem)"
            stem.contains("endokrin", true) || stem.contains("endocrin", true) -> "الغدد الصماء ($stem)"
            stem.contains("duyu", true) || stem.contains("eye", true) || stem.contains("goz", true) || stem.contains("ear", true) || stem.contains("kulak", true) -> "أعضاء الحس ($stem)"
            stem.contains("organ", true) || stem.contains("viscera", true) || stem.contains("splanchn", true) -> "الأعضاء الداخلية ($stem)"
            stem.contains("dolasim", true) || stem.contains("circul", true) || stem.contains("damar", true) || stem.contains("vasc", true) -> "الجهاز الدوري ($stem)"
            stem.contains("iskelet", true) || stem.contains("skeleton", true) || stem.contains("bone", true) -> "الهيكل العظمي ($stem)"
            stem.contains("eklem", true) || stem.contains("joint", true) || stem.contains("bag", true) || stem.contains("ligament", true) -> "المفاصل والأربطة ($stem)"
            stem.contains("kas", true) || stem.contains("muscle", true) || stem.contains("myo", true) -> "العضلات ($stem)"
            stem.contains("skin", true) || stem.contains("deri", true) || stem.contains("integument", true) -> "الجلد ($stem)"
            stem.contains("body", true) -> "الجسم الكامل ($stem)"
            else -> stem.replace('_', ' ').replace('-', ' ')
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

    private fun readAllBytes(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buf)
            if (read <= 0) break
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }
}
