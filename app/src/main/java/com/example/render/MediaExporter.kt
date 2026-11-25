package com.example.render

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ExportResult {
    data class SavedToGallery(val uri: Uri, val message: String) : ExportResult()
    data class SharedViaFile(val uri: Uri, val mimeType: String, val message: String) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

object MediaExporter {

    private fun timestampName(prefix: String, ext: String): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "${prefix}_${sdf.format(Date())}.$ext"
    }

    suspend fun saveSnapshot(
        context: Context,
        previewBitmap: Bitmap?,
        width: Int,
        height: Int,
        arabicTypeface: Typeface,
        drawOverlay: (Canvas, Int, Int) -> Unit,
    ): ExportResult = withContext(Dispatchers.IO) {
        if (width <= 0 || height <= 0) {
            return@withContext ExportResult.Error("تعذّر التقاط الصورة: أبعاد الشاشة غير صالحة.")
        }
        return@withContext try {
            val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outBitmap)
            canvas.drawColor(Color.BLACK)
            if (previewBitmap != null && !previewBitmap.isRecycled) {
                canvas.drawBitmap(previewBitmap, null, Rect(0, 0, width, height), null)
            }
            drawOverlay(canvas, width, height)

            // Educational watermark at bottom
            val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 255, 255, 255)
                textSize = maxOf(14f, height * 0.017f)
                textAlign = Paint.Align.CENTER
                typeface = arabicTypeface
            }
            canvas.drawText(
                "نموذج تشريحي تعليمي وليس أشعة حقيقية",
                width / 2f,
                height - maxOf(20f, height * 0.025f),
                wmPaint,
            )

            val fileName = timestampName("anatomy_lens", "png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AnatomyLens")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext ExportResult.Error("تعذّر إنشاء ملف الصورة.")
                resolver.openOutputStream(uri)?.use { stream ->
                    outBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                outBitmap.recycle()
                ExportResult.SavedToGallery(uri, "تم حفظ اللقطة في المعرض (Pictures/AnatomyLens).")
            } else {
                val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                val file = File(dir, fileName)
                FileOutputStream(file).use { stream ->
                    outBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                outBitmap.recycle()
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                ExportResult.SharedViaFile(uri, "image/png", "تم تجهيز اللقطة للمشاركة أو الحفظ.")
            }
        } catch (e: Exception) {
            ExportResult.Error("تعذّر حفظ اللقطة: ${e.localizedMessage ?: "خطأ غير متوقع"}")
        }
    }

    fun createShareIntent(uri: Uri, mimeType: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun finalizeVideoToGallery(context: Context, tempFile: File): ExportResult = withContext(Dispatchers.IO) {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            return@withContext ExportResult.Error("ملف الفيديو فارغ.")
        }
        return@withContext try {
            val fileName = timestampName("anatomy_lens", "mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/AnatomyLens")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext ExportResult.Error("تعذّر حفظ الفيديو.")
                resolver.openOutputStream(uri)?.use { out ->
                    tempFile.inputStream().use { inp -> inp.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                tempFile.delete()
                ExportResult.SavedToGallery(uri, "تم حفظ التسجيل في المعرض (Movies/AnatomyLens).")
            } else {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                ExportResult.SharedViaFile(uri, "video/mp4", "تم تجهيز الفيديو للمشاركة أو الحفظ.")
            }
        } catch (e: Exception) {
            ExportResult.Error("تعذّر حفظ الفيديو: ${e.localizedMessage ?: "خطأ غير متوقع"}")
        }
    }
}

class OverlayVideoRecorder(
    private val outputFile: File,
    val videoWidth: Int,
    val videoHeight: Int,
    private val arabicTypeface: Typeface,
) {
    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private val bufferInfo = MediaCodec.BufferInfo()
    private var startNano = 0L

    private val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textSize = maxOf(16f, videoHeight * 0.017f)
        textAlign = Paint.Align.CENTER
        typeface = arabicTypeface
    }

    fun start(): Boolean {
        return try {
            outputFile.parentFile?.mkdirs()
            val w = (videoWidth / 2) * 2
            val h = (videoHeight / 2) * 2
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 3_500_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 24)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = c.createInputSurface()
            c.start()
            codec = c
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            startNano = System.nanoTime()
            true
        } catch (_: Exception) {
            releaseSilently()
            false
        }
    }

    fun drawFrame(previewBitmap: Bitmap?, drawOverlay: (Canvas, Int, Int) -> Unit) {
        val surface = inputSurface ?: return
        val c = codec ?: return
        try {
            val canvas = surface.lockCanvas(null)
            try {
                canvas.drawColor(Color.BLACK)
                if (previewBitmap != null && !previewBitmap.isRecycled) {
                    canvas.drawBitmap(previewBitmap, null, Rect(0, 0, videoWidth, videoHeight), null)
                }
                drawOverlay(canvas, videoWidth, videoHeight)
                canvas.drawText(
                    "نموذج تشريحي تعليمي وليس أشعة حقيقية",
                    videoWidth / 2f,
                    videoHeight - maxOf(22f, videoHeight * 0.025f),
                    watermarkPaint,
                )
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
            drainEncoder(c, endOfStream = false)
        } catch (_: Exception) {
        }
    }

    fun stop(): File? {
        val c = codec ?: return null
        return try {
            c.signalEndOfInputStream()
            drainEncoder(c, endOfStream = true)
            releaseSilently()
            if (outputFile.exists() && outputFile.length() > 0L) outputFile else null
        } catch (_: Exception) {
            releaseSilently()
            null
        }
    }

    private fun drainEncoder(c: MediaCodec, endOfStream: Boolean) {
        val timeoutUs = if (endOfStream) 10_000L else 0L
        var loops = 0
        while (loops++ < 200) {
            val outIndex = c.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxer?.addTrack(c.outputFormat) ?: -1
                        if (trackIndex >= 0) {
                            muxer?.start()
                            muxerStarted = true
                        }
                    }
                }
                outIndex >= 0 -> {
                    val encodedData = c.getOutputBuffer(outIndex)
                    if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer?.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    c.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }
    }

    private fun releaseSilently() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { inputSurface?.release() }
        if (muxerStarted) {
            runCatching { muxer?.stop() }
        }
        runCatching { muxer?.release() }
        codec = null
        inputSurface = null
        muxer = null
        muxerStarted = false
    }
}
