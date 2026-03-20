package com.message.bulksend.tools.fileopener

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.message.bulksend.pdfviewer.PdfViewerActivity
import com.message.bulksend.tablesheet.TableSheetActivity
import java.util.Locale

enum class FileViewerTarget {
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    PDF,
    TABLE,
    UNKNOWN
}

data class OpenableFile(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val extension: String,
    val target: FileViewerTarget
)

object FileOpenRouter {
    const val EXTRA_FILE_NAME = "file_opener.extra.FILE_NAME"
    const val EXTRA_FILE_MIME = "file_opener.extra.FILE_MIME"

    private val textExtensions = setOf("txt", "log", "md", "markdown", "json", "xml", "yaml", "yml")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "heif", "gif")
    private val audioExtensions = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac")
    private val videoExtensions = setOf("mp4", "mkv", "mov", "avi", "3gp", "webm")
    private val tableExtensions = setOf("csv", "xls", "xlsx", "vcf")

    fun extractUri(intent: Intent): Uri? {
        return intent.data
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
    }

    fun inspect(context: Context, uri: Uri, hintedMimeType: String? = null): OpenableFile {
        val displayName = resolveDisplayName(context, uri)
        val extension = resolveExtension(displayName, uri)
        val mimeType = normalizeMimeType(context, uri, extension, hintedMimeType)
        val target = determineTarget(mimeType, extension)
        return OpenableFile(
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            extension = extension,
            target = target
        )
    }

    fun routeToViewer(context: Context, uri: Uri, hintedMimeType: String? = null): Boolean {
        val file = inspect(context, uri, hintedMimeType)
        val targetIntent = createTargetIntent(context, file) ?: return false
        if (context !is Activity) {
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(targetIntent)
        return true
    }

    private fun createTargetIntent(context: Context, file: OpenableFile): Intent? {
        return when (file.target) {
            FileViewerTarget.TEXT -> Intent(context, TextFileViewerActivity::class.java).apply {
                data = file.uri
            }
            FileViewerTarget.IMAGE -> Intent(context, ImageViewerActivity::class.java).apply {
                data = file.uri
            }
            FileViewerTarget.AUDIO -> Intent(context, AudioPlayerActivity::class.java).apply {
                data = file.uri
            }
            FileViewerTarget.VIDEO -> Intent(context, VideoPlayerActivity::class.java).apply {
                data = file.uri
            }
            FileViewerTarget.PDF -> Intent(context, PdfViewerActivity::class.java).apply {
                data = file.uri
            }
            FileViewerTarget.TABLE -> Intent(context, TableSheetActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                if (file.mimeType != null) {
                    setDataAndType(file.uri, file.mimeType)
                } else {
                    data = file.uri
                }
            }
            FileViewerTarget.UNKNOWN -> null
        }?.apply {
            putExtra(EXTRA_FILE_NAME, file.displayName)
            putExtra(EXTRA_FILE_MIME, file.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        val queriedName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }

        return queriedName
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "File"
    }

    private fun resolveExtension(displayName: String, uri: Uri): String {
        val fromName = displayName.substringAfterLast('.', "").lowercase(Locale.US)
        if (fromName.isNotBlank()) {
            return fromName
        }

        val path = uri.toString().substringBefore('?')
        return path.substringAfterLast('.', "").lowercase(Locale.US)
    }

    private fun normalizeMimeType(
        context: Context,
        uri: Uri,
        extension: String,
        hintedMimeType: String?
    ): String? {
        val resolved = listOfNotNull(
            hintedMimeType,
            context.contentResolver.getType(uri)
        ).firstOrNull { !it.isNullOrBlank() }
            ?.lowercase(Locale.US)

        if (!resolved.isNullOrBlank() && resolved != "application/octet-stream") {
            return resolved
        }

        return when (extension) {
            "txt", "log", "md", "markdown" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "yaml", "yml" -> "application/x-yaml"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "gif" -> "image/gif"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a", "aac" -> "audio/mp4"
            "flac" -> "audio/flac"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            "webm" -> "video/webm"
            "pdf" -> "application/pdf"
            "csv" -> "text/csv"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "vcf" -> "text/x-vcard"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.US))
        }
    }

    private fun determineTarget(mimeType: String?, extension: String): FileViewerTarget {
        val normalizedMime = mimeType?.lowercase(Locale.US).orEmpty()
        return when {
            normalizedMime == "application/pdf" || extension == "pdf" -> FileViewerTarget.PDF
            normalizedMime.startsWith("image/") || extension in imageExtensions -> FileViewerTarget.IMAGE
            normalizedMime.startsWith("audio/") || extension in audioExtensions -> FileViewerTarget.AUDIO
            normalizedMime.startsWith("video/") || extension in videoExtensions -> FileViewerTarget.VIDEO
            normalizedMime == "text/plain" ||
                normalizedMime == "text/markdown" ||
                normalizedMime == "application/json" ||
                normalizedMime == "application/xml" ||
                normalizedMime == "text/xml" ||
                normalizedMime == "application/x-yaml" ||
                extension in textExtensions -> FileViewerTarget.TEXT
            normalizedMime == "text/csv" ||
                normalizedMime == "text/comma-separated-values" ||
                normalizedMime == "application/csv" ||
                normalizedMime == "application/vnd.ms-excel" ||
                normalizedMime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
                normalizedMime == "text/x-vcard" ||
                normalizedMime == "text/vcard" ||
                extension in tableExtensions -> FileViewerTarget.TABLE
            else -> FileViewerTarget.UNKNOWN
        }
    }
}
