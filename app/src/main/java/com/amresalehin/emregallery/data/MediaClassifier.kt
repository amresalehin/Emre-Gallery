package com.amresalehin.emregallery.data

import java.util.Locale

/**
 * High-level category for discovered media.
 */
enum class MediaCategory(val label: String) {
    ALL("All"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    STREAMS("Streams")
}

/**
 * Extraction source indicator for transparency.
 */
enum class ExtractionSource(val label: String) {
    GALLERY_DL("gallery-dl"),
    STATIC_HTML("HTML"),
    DOM_SCRAPE("DOM"),
    CSS_BACKGROUND("CSS"),
    SRCSET("Srcset"),
    NETWORK_INTERCEPT("Network"),
    IFRAME("iFrame")
}

data class ClassificationResult(
    val category: MediaCategory,
    val format: String,
    val mimeType: String,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val isStream: Boolean
)

/**
 * Classifies media URLs and MIME hints into category, format, MIME type,
 * and derives clean fallback filenames.
 */
object MediaClassifier {

    val KNOWN_MEDIA_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "svg", "avif", "bmp", "heic", "heif", "ico",
        "mp4", "webm", "mov", "mkv", "avi", "flv", "3gp", "m4v", "ts", "m3u8", "mpd",
        "mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "wma"
    )

    /**
     * Checks if a media item is an SVG vector asset based on format, MIME type, or URL extension.
     */
    fun isSvg(url: String, mimeHint: String? = null, format: String? = null): Boolean {
        if (format?.equals("SVG", ignoreCase = true) == true) return true
        if (mimeHint?.contains("svg", ignoreCase = true) == true) return true
        val clean = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return clean.endsWith(".svg")
    }

    /**
     * Classifies a media URL into category (IMAGE, VIDEO, AUDIO, STREAM) and specific format string.
     */
    fun classify(url: String, mimeHint: String? = null): ClassificationResult {
        val lower = url.lowercase(Locale.ROOT)
        val mimeLower = mimeHint?.lowercase(Locale.ROOT) ?: ""
        val cleanPath = lower.substringBefore("?").substringBefore("#").trimEnd('/')
        val queryPart = lower.substringAfter("?", "").substringBefore("#")
        val queryParams = parseQueryParams(queryPart)
        val queryFormatExt = (queryParams["format"] ?: queryParams["fmt"] ?: queryParams["ext"] ?: "")
            .trim('.')
            .lowercase(Locale.ROOT)
        val queryMime = (queryParams["mime"] ?: queryParams["content-type"] ?: "").lowercase(Locale.ROOT)
        val effectiveMime = if (queryMime.isNotBlank() && mimeLower.isBlank()) queryMime else "$mimeLower $queryMime".trim()

        // 1. Streaming media (HLS & DASH)
        val isHlsManifest = cleanPath.endsWith(".m3u8") || queryFormatExt == "m3u8" ||
            effectiveMime.contains("x-mpegurl") || effectiveMime.contains("vnd.apple.mpegurl") ||
            effectiveMime.contains("application/mpegurl")
        if (isHlsManifest) {
            return ClassificationResult(
                category = MediaCategory.STREAMS,
                format = "HLS (m3u8)",
                mimeType = "application/x-mpegURL",
                isVideo = true,
                isAudio = false,
                isStream = true
            )
        }
        val isDashManifest = cleanPath.endsWith(".mpd") || queryFormatExt == "mpd" ||
            effectiveMime.contains("dash+xml") || effectiveMime.contains("video/vnd.mpeg.dash.mpd")
        if (isDashManifest) {
            return ClassificationResult(
                category = MediaCategory.STREAMS,
                format = "DASH (mpd)",
                mimeType = "application/dash+xml",
                isVideo = true,
                isAudio = false,
                isStream = true
            )
        }

        // 2. Video formats
        classifyVideo(cleanPath, queryFormatExt, effectiveMime)?.let { return it }

        // 3. Audio formats
        classifyAudio(cleanPath, queryFormatExt, effectiveMime)?.let { return it }

        // 4. Image formats
        val format = when {
            cleanPath.endsWith(".webp") || queryFormatExt == "webp" || effectiveMime.contains("webp") -> "WEBP"
            cleanPath.endsWith(".png") || queryFormatExt == "png" || effectiveMime.contains("png") -> "PNG"
            cleanPath.endsWith(".gif") || queryFormatExt == "gif" || effectiveMime.contains("gif") -> "GIF"
            cleanPath.endsWith(".svg") || queryFormatExt == "svg" || effectiveMime.contains("svg") -> "SVG"
            cleanPath.endsWith(".avif") || queryFormatExt == "avif" || effectiveMime.contains("avif") -> "AVIF"
            cleanPath.endsWith(".bmp") || queryFormatExt == "bmp" || effectiveMime.contains("bmp") -> "BMP"
            else -> "JPEG"
        }
        val imgMime = when (format) {
            "WEBP" -> "image/webp"
            "PNG" -> "image/png"
            "GIF" -> "image/gif"
            "SVG" -> "image/svg+xml"
            "AVIF" -> "image/avif"
            "BMP" -> "image/bmp"
            else -> "image/jpeg"
        }

        return ClassificationResult(
            category = MediaCategory.IMAGES,
            format = format,
            mimeType = imgMime,
            isVideo = false,
            isAudio = false,
            isStream = false
        )
    }

    private fun parseQueryParams(queryPart: String): Map<String, String> {
        if (queryPart.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (pair in queryPart.split('&')) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx > 0) {
                val k = pair.substring(0, eqIdx)
                val v = pair.substring(eqIdx + 1)
                    .replace("%2f", "/", ignoreCase = true)
                    .replace("%2b", "+", ignoreCase = true)
                result[k] = v
            }
        }
        return result
    }

    private fun classifyVideo(
        cleanPath: String,
        queryFormatExt: String,
        mimeLower: String
    ): ClassificationResult? {
        if (cleanPath.endsWith(".mp4") || cleanPath.endsWith(".m4v") ||
            queryFormatExt == "mp4" || queryFormatExt == "m4v" ||
            mimeLower.contains("video/mp4")
        ) {
            return videoResult("MP4", "video/mp4")
        }
        if (cleanPath.endsWith(".webm") || queryFormatExt == "webm" || mimeLower.contains("video/webm")) {
            return videoResult("WEBM", "video/webm")
        }
        if (cleanPath.endsWith(".mov") || queryFormatExt == "mov" || mimeLower.contains("video/quicktime")) {
            return videoResult("MOV", "video/quicktime")
        }
        if (cleanPath.endsWith(".mkv") || queryFormatExt == "mkv" || mimeLower.contains("video/x-matroska")) {
            return videoResult("MKV", "video/x-matroska")
        }
        if (mimeLower.startsWith("video/")) {
            val sub = mimeLower.substringAfter("video/").substringBefore(' ').uppercase(Locale.ROOT)
            return videoResult(sub, mimeLower.substringBefore(' '))
        }
        return null
    }

    private fun classifyAudio(
        cleanPath: String,
        queryFormatExt: String,
        mimeLower: String
    ): ClassificationResult? {
        if (cleanPath.endsWith(".mp3") || queryFormatExt == "mp3" ||
            mimeLower.contains("audio/mpeg") || mimeLower.contains("audio/mp3")
        ) {
            return audioResult("MP3", "audio/mpeg")
        }
        if (cleanPath.endsWith(".m4a") || cleanPath.endsWith(".aac") ||
            queryFormatExt == "m4a" || queryFormatExt == "aac" ||
            mimeLower.contains("audio/aac") || mimeLower.contains("audio/mp4")
        ) {
            val isAac = cleanPath.endsWith(".aac") || queryFormatExt == "aac" || mimeLower.contains("audio/aac")
            return audioResult(if (isAac) "AAC" else "M4A", "audio/mp4")
        }
        if (cleanPath.endsWith(".wav") || queryFormatExt == "wav" ||
            mimeLower.contains("audio/wav") || mimeLower.contains("audio/x-wav")
        ) {
            return audioResult("WAV", "audio/wav")
        }
        if (cleanPath.endsWith(".ogg") || cleanPath.endsWith(".opus") ||
            queryFormatExt == "ogg" || queryFormatExt == "opus" ||
            mimeLower.contains("audio/ogg") || mimeLower.contains("audio/opus")
        ) {
            val isOpus = cleanPath.endsWith(".opus") || queryFormatExt == "opus" || mimeLower.contains("audio/opus")
            return audioResult(if (isOpus) "OPUS" else "OGG", "audio/ogg")
        }
        if (cleanPath.endsWith(".flac") || queryFormatExt == "flac" || mimeLower.contains("audio/flac")) {
            return audioResult("FLAC", "audio/flac")
        }
        if (mimeLower.startsWith("audio/")) {
            val sub = mimeLower.substringAfter("audio/").substringBefore(' ').uppercase(Locale.ROOT)
            return audioResult(sub, mimeLower.substringBefore(' '))
        }
        return null
    }

    private fun videoResult(format: String, mimeType: String) = ClassificationResult(
        category = MediaCategory.VIDEOS,
        format = format,
        mimeType = mimeType,
        isVideo = true,
        isAudio = false,
        isStream = false
    )

    private fun audioResult(format: String, mimeType: String) = ClassificationResult(
        category = MediaCategory.AUDIO,
        format = format,
        mimeType = mimeType,
        isVideo = false,
        isAudio = true,
        isStream = false
    )

    /**
     * Derives a clean filename from URL and format.
     */
    fun deriveFileName(url: String, format: String, fallbackIndex: Int): String {
        return runCatching {
            val cleanUrl = url.substringBefore("?").substringBefore("#")
            val lastSlash = cleanUrl.lastIndexOf('/')
            val segment = if (lastSlash != -1 && lastSlash < cleanUrl.length - 1) cleanUrl.substring(lastSlash + 1) else ""
            val extInSegment = segment.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (segment.length > 2 && extInSegment in KNOWN_MEDIA_EXTENSIONS) {
                segment
            } else {
                val ext = when (format.uppercase(Locale.ROOT)) {
                    "HLS (M3U8)", "HLS" -> "m3u8"
                    "DASH (MPD)", "DASH" -> "mpd"
                    "MP4" -> "mp4"
                    "WEBM" -> "webm"
                    "MOV" -> "mov"
                    "MP3" -> "mp3"
                    "M4A" -> "m4a"
                    "AAC" -> "aac"
                    "WAV" -> "wav"
                    "FLAC" -> "flac"
                    "WEBP" -> "webp"
                    "PNG" -> "png"
                    "GIF" -> "gif"
                    "SVG" -> "svg"
                    "AVIF" -> "avif"
                    else -> "jpg"
                }
                "media_${fallbackIndex + 1}.$ext"
            }
        }.getOrDefault("media_${fallbackIndex + 1}.bin")
    }
}
