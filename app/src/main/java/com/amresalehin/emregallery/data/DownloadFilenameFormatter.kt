package com.amresalehin.emregallery.data

import java.net.URL
import java.util.Locale

/**
 * Formats download filenames using yt-dlp style template placeholders
 * (%(title)s, %(uploader)s, %(upload_date)s, %(id)s, %(ext)s, %(domain)s)
 * and resolves safe domain subfolder names.
 */
object DownloadFilenameFormatter {

    const val DEFAULT_FILENAME_TEMPLATE = "%(title)s"

    val TEMPLATE_PRESETS: List<Pair<String, String>> = listOf(
        "Title" to "%(title)s",
        "Uploader - Title" to "%(uploader)s - %(title)s",
        "Date - Uploader - Title" to "%(upload_date)s - %(uploader)s - %(title)s",
        "Title [ID]" to "%(title)s [%(id)s]"
    )

    val SUPPORTED_TOKENS: List<String> = listOf(
        "%(title)s",
        "%(uploader)s",
        "%(upload_date)s",
        "%(id)s"
    )

    private val invalidFilenameChars = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+")
    private val multiSpace = Regex("\\s{2,}")
    private val repeatedSeparators = Regex("(?:\\s*-\\s*){2,}")
    private val repeatedUnderscores = Regex("_{2,}")

    fun sanitizeComponent(raw: String, maxLen: Int = 96): String {
        val cleaned = raw
            .replace(invalidFilenameChars, "_")
            .replace(multiSpace, " ")
            .trim()
            .trim('.', '-', '_')
        return if (cleaned.length > maxLen) cleaned.take(maxLen).trim() else cleaned
    }

    fun sanitizeDomainFolder(domainOrUrl: String?): String? {
        val raw = domainOrUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val host = runCatching {
            val withScheme = if (raw.contains("://")) raw else "https://$raw"
            URL(withScheme).host
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: raw.substringBefore('/')

        val normalized = host
            .lowercase(Locale.ROOT)
            .removePrefix("www.")
            .substringBefore(':')
            .replace(Regex("[^a-z0-9._-]"), "_")
            .trim('.', '-', '_')
        return normalized.takeIf { it.isNotBlank() }
    }

    fun inferExtension(title: String, format: String, url: String, isAudio: Boolean = false, isVideo: Boolean = false): String {
        val titleExt = title.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (titleExt in KNOWN_EXTENSIONS) return titleExt

        val fmt = format.trim().lowercase(Locale.ROOT)
        if (fmt in KNOWN_EXTENSIONS) return fmt

        val urlClean = url.substringBefore('?').substringBefore('#')
        val urlExt = urlClean.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (urlExt in KNOWN_EXTENSIONS) return urlExt

        return when {
            isAudio -> "m4a"
            isVideo -> "mp4"
            else -> "jpg"
        }
    }

    fun formatFileName(
        template: String?,
        title: String,
        ext: String,
        uploader: String? = null,
        uploadDate: String? = null,
        mediaId: String? = null,
        domain: String? = null
    ): String {
        val cleanExt = ext.trim().trimStart('.').lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
            .ifBlank { "mp4" }

        val rawTitle = title.trim().let { t ->
            if (t.endsWith(".$cleanExt", ignoreCase = true)) {
                t.dropLast(cleanExt.length + 1)
            } else {
                val dotExt = t.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (dotExt in KNOWN_EXTENSIONS) t.substringBeforeLast('.') else t
            }
        }
        val safeTitle = sanitizeComponent(rawTitle).ifBlank { "media" }
        val safeUploader = uploader?.let { sanitizeComponent(it, 48) }.orEmpty()
        val safeDate = uploadDate?.let { sanitizeComponent(it, 16) }.orEmpty()
        val safeId = mediaId?.let { sanitizeComponent(it, 40) }.orEmpty()
        val safeDomain = sanitizeDomainFolder(domain).orEmpty()

        val activeTemplate = template?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_FILENAME_TEMPLATE
        var rendered = activeTemplate
            .replace("%(title)s", safeTitle)
            .replace("%(uploader)s", safeUploader)
            .replace("%(upload_date)s", safeDate)
            .replace("%(id)s", safeId)
            .replace("%(domain)s", safeDomain)
            .replace("%(ext)s", cleanExt)

        // Clean empty bracketed wrappers and duplicate separators when optional fields are absent
        rendered = rendered
            .replace(Regex("\\[\\s*]"), "")
            .replace(Regex("\\(\\s*\\)"), "")
            .replace(repeatedSeparators, " - ")
            .replace(repeatedUnderscores, "_")
            .replace(multiSpace, " ")
            .trim()
            .trim('-', '_', '.')

        val stem = sanitizeComponent(rendered, maxLen = 120).ifBlank { safeTitle }
        return if (activeTemplate.contains("%(ext)s") && stem.endsWith(".$cleanExt", ignoreCase = true)) {
            stem
        } else if (stem.endsWith(".$cleanExt", ignoreCase = true)) {
            stem
        } else {
            "$stem.$cleanExt"
        }
    }

    fun formatDurationSeconds(seconds: Long?): String? {
        if (seconds == null || seconds <= 0L) return null
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        val secs = seconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }

    fun formatFileSize(sizeBytes: Long?): String? {
        if (sizeBytes == null || sizeBytes <= 0L) return null
        return when {
            sizeBytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0))
            sizeBytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
            sizeBytes >= 1024L -> "${sizeBytes / 1024L} KB"
            else -> "$sizeBytes B"
        }
    }

    private val KNOWN_EXTENSIONS = setOf(
        "mp4", "webm", "mkv", "mov", "avi", "m4v",
        "m4a", "mp3", "opus", "aac", "ogg", "flac", "wav",
        "jpg", "jpeg", "png", "webp", "gif", "avif", "svg", "bmp"
    )
}
