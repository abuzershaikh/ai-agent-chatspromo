package com.message.bulksend.tablesheet.extractor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.LinkedHashSet
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class SheetDataExtractor(
    private val context: Context
) {
    suspend fun extract(
        uri: Uri,
        onProgress: ((ExtractorProgress) -> Unit)? = null
    ): ExtractorResult = withContext(Dispatchers.IO) {
        reportProgress(onProgress, 0.05f, "Processing file...")
        val mime = context.contentResolver.getType(uri).orEmpty()
        val displayName = queryDisplayName(uri).orEmpty()
        val lowerName = displayName.lowercase(Locale.ROOT)
        reportProgress(onProgress, 0.12f, "Scanning content...")

        val sourceLabel: String
        val extracted =
            when {
                mime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mov") -> {
                    sourceLabel = "video"
                    reportProgress(onProgress, 0.18f, "Scanning video content...")
                    extractEntitiesFromVideo(uri, onProgress)
                }
                mime.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp") -> {
                    sourceLabel = "image"
                    reportProgress(onProgress, 0.28f, "Scanning image content...")
                    TextEntityExtractor.extract(extractTextFromImage(uri))
                }
                mime.startsWith("text/") || lowerName.endsWith(".txt") || lowerName.endsWith(".csv") -> {
                    sourceLabel = "text"
                    reportProgress(onProgress, 0.28f, "Scanning text content...")
                    TextEntityExtractor.extract(readTextFile(uri))
                }
                else -> {
                    sourceLabel = "text"
                    reportProgress(onProgress, 0.28f, "Scanning file content...")
                    TextEntityExtractor.extract(readTextFile(uri))
                }
            }

        reportProgress(onProgress, 0.92f, "Finalizing results...")
        val (emails, phones) = extracted
        reportProgress(onProgress, 0.98f, "Saving results...")
        ExtractorResult(
            emails = emails,
            phoneNumbers = phones,
            sourceLabel = sourceLabel
        )
    }

    private suspend fun extractTextFromImage(uri: Uri): String {
        val bitmap = decodeBitmap(uri) ?: return ""
        return recognizeBitmap(bitmap)
    }

    private suspend fun extractEntitiesFromVideo(
        uri: Uri,
        onProgress: ((ExtractorProgress) -> Unit)?
    ): Pair<List<String>, List<String>> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L

            val frameTimesMs = computeFrameTimes(durationMs)
            val frameBitmaps = mutableListOf<Bitmap>()
            frameTimesMs.forEachIndexed { index, timeMs ->
                val bitmap =
                    retriever.getFrameAtTime(
                        timeMs * 1_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    ) ?: return@forEachIndexed
                frameBitmaps += resizeBitmapIfNeeded(bitmap)
                val frameProgress = (index + 1).toFloat() / frameTimesMs.size.coerceAtLeast(1)
                reportProgress(
                    onProgress,
                    fraction = 0.18f + (0.20f * frameProgress),
                    message = "Scanning video content..."
                )
            }

            val semaphore = Semaphore(VIDEO_OCR_PARALLELISM)
            val completedFrames = AtomicInteger(0)
            val totalFrames = frameBitmaps.size.coerceAtLeast(1)
            val pairs =
                coroutineScope {
                    frameBitmaps.map { frameBitmap ->
                        async(Dispatchers.Default) {
                            semaphore.withPermit {
                                val text = recognizeBitmap(frameBitmap)
                                val pair = TextEntityExtractor.extract(text)
                                val done = completedFrames.incrementAndGet()
                                val ocrProgress = done.toFloat() / totalFrames
                                reportProgress(
                                    onProgress,
                                    fraction = 0.40f + (0.50f * ocrProgress),
                                    message = "Processing video content..."
                                )
                                pair
                            }
                        }
                    }.awaitAll()
                }

            val emailSet = LinkedHashSet<String>()
            val phoneSet = LinkedHashSet<String>()
            pairs.forEach { (emails, phones) ->
                emails.forEach { emailSet += it }
                phones.forEach { phoneSet += it }
            }
            reportProgress(onProgress, 0.92f, "Video scan completed.")

            emailSet.toList() to phoneSet.toList()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun computeFrameTimes(durationMs: Long): List<Long> {
        if (durationMs <= 0L) return listOf(0L)
        val targetFrames = (durationMs / 900L).toInt().coerceIn(8, 36)
        val step = (durationMs / targetFrames).coerceAtLeast(600L)
        val values = mutableListOf<Long>()
        var current = 0L
        while (current <= durationMs && values.size < 36) {
            values += current
            current += step
        }
        if (values.isEmpty()) values += 0L
        return values
    }

    private suspend fun recognizeBitmap(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage).await().text.orEmpty()
        } finally {
            runCatching { bitmap.recycle() }
            runCatching { recognizer.close() }
        }
    }

    private fun resizeBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width.coerceAtLeast(1)
        val height = bitmap.height.coerceAtLeast(1)
        val maxSide = maxOf(width, height)
        if (maxSide <= 1400) return bitmap

        val ratio = 1400f / maxSide.toFloat()
        val newWidth = (width * ratio).toInt().coerceAtLeast(1)
        val newHeight = (height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        if (scaled !== bitmap) {
            runCatching { bitmap.recycle() }
        }
        return scaled
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val srcW = info.size.width.coerceAtLeast(1)
                val srcH = info.size.height.coerceAtLeast(1)
                val maxSide = maxOf(srcW, srcH)
                if (maxSide > 1600) {
                    val ratio = 1600f / maxSide.toFloat()
                    decoder.setTargetSize(
                        (srcW * ratio).toInt().coerceAtLeast(1),
                        (srcH * ratio).toInt().coerceAtLeast(1)
                    )
                }
            }
        }.getOrNull()
    }

    private fun readTextFile(uri: Uri): String {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            }.orEmpty()
        }.getOrDefault("")
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }

    private fun reportProgress(
        callback: ((ExtractorProgress) -> Unit)?,
        fraction: Float,
        message: String
    ) {
        callback?.invoke(
            ExtractorProgress(
                fraction = fraction.coerceIn(0f, 1f),
                message = message
            )
        )
    }

    private companion object {
        const val VIDEO_OCR_PARALLELISM = 2
    }
}
