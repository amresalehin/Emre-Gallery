package com.amresalehin.emregallery.data

import org.json.JSONArray
import java.util.Locale

/**
 * Represents a selectable stream/container/quality format (including Audio-Only tracks)
 * extracted for a [ScrapedMediaItem].
 */
data class MediaFormatOption(
    val formatId: String,
    val label: String,
    val ext: String,
    val width: Int? = null,
    val height: Int? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val filesize: Long? = null,
    val tbr: Double? = null,
    val abr: Double? = null,
    val fps: Int? = null,
    val formatNote: String? = null,
    val url: String? = null,
    val isAudioOnly: Boolean = false,
    val hasVideo: Boolean = true,
    val hasAudio: Boolean = true,
    val ytDlpSelector: String = formatId
)

object MediaFormatSelectionHelper {

    const val PRESET_BEST = "best"
    const val PRESET_1080P = "1080p"
    const val PRESET_720P = "720p"
    const val PRESET_480P = "480p"
    const val PRESET_AUDIO_ONLY = "audio_only"

    val QUALITY_PRESETS: List<Pair<String, String>> = listOf(
        PRESET_BEST to "Best",
        PRESET_1080P to "1080p",
        PRESET_720P to "720p",
        PRESET_480P to "480p",
        PRESET_AUDIO_ONLY to "Audio Only"
    )

    fun presetLabel(preset: String): String = when (preset) {
        PRESET_1080P -> "1080p"
        PRESET_720P -> "720p"
        PRESET_480P -> "480p"
        PRESET_AUDIO_ONLY -> "Audio Only"
        else -> "Best"
    }

    fun selectorForPreset(preset: String): String? = when (preset) {
        PRESET_1080P -> "b[height<=1080][vcodec!=none][acodec!=none]/best[height<=1080][vcodec!=none][acodec!=none]/best[height<=1080]/best"
        PRESET_720P -> "b[height<=720][vcodec!=none][acodec!=none]/best[height<=720][vcodec!=none][acodec!=none]/best[height<=720]/best"
        PRESET_480P -> "b[height<=480][vcodec!=none][acodec!=none]/best[height<=480][vcodec!=none][acodec!=none]/best[height<=480]/best"
        PRESET_AUDIO_ONLY -> "bestaudio[ext=m4a]/bestaudio/best"
        else -> null
    }

    fun parseOptions(item: ScrapedMediaItem): List<MediaFormatOption> {
        val rawJson = item.metadata["formats_json"]
        val parsed = mutableListOf<MediaFormatOption>()

        if (!rawJson.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(rawJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val fmtId = obj.optString("format_id", "").trim()
                    val url = obj.optString("url", "").trim().takeIf { it.isNotBlank() }
                    val ext = obj.optString("ext", "mp4").lowercase(Locale.ROOT).ifBlank { "mp4" }
                    val w = obj.optInt("width", 0).takeIf { it > 0 }
                    val h = obj.optInt("height", 0).takeIf { it > 0 }
                    val fps = obj.optInt("fps", 0).takeIf { it > 0 }
                    val vcodec = obj.optString("vcodec", "").takeIf { it.isNotBlank() && it != "null" }
                    val acodec = obj.optString("acodec", "").takeIf { it.isNotBlank() && it != "null" }
                    val note = obj.optString("format_note", "").takeIf { it.isNotBlank() && it != "null" }
                    val filesize = if (obj.has("filesize") && !obj.isNull("filesize")) {
                        obj.optLong("filesize", -1L).takeIf { it > 0L }
                    } else null
                    val tbr = if (obj.has("tbr") && !obj.isNull("tbr")) {
                        obj.optDouble("tbr", -1.0).takeIf { it > 0.0 }
                    } else null
                    val abr = if (obj.has("abr") && !obj.isNull("abr")) {
                        obj.optDouble("abr", -1.0).takeIf { it > 0.0 }
                    } else null

                    val hasVideo = if (obj.has("has_video")) {
                        obj.optBoolean("has_video")
                    } else {
                        vcodec != null && vcodec != "none"
                    }
                    val hasAudio = if (obj.has("has_audio")) {
                        obj.optBoolean("has_audio")
                    } else {
                        acodec != null && acodec != "none"
                    }
                    val isAudioOnly = if (obj.has("is_audio_only")) {
                        obj.optBoolean("is_audio_only")
                    } else {
                        !hasVideo && (hasAudio || ext in setOf("mp3", "m4a", "opus", "aac", "ogg", "flac", "wav"))
                    }

                    val codecTag = formatCodecShortName(if (isAudioOnly) acodec else vcodec)
                    val bitrateText = (abr ?: tbr)?.let { "${it.toInt()}k" }
                    val sizeText = DownloadFilenameFormatter.formatFileSize(filesize)

                    val labelParts = buildList {
                        if (isAudioOnly) {
                            add("Audio Only")
                            add(ext.uppercase(Locale.ROOT))
                        } else {
                            if (h != null) add("${h}p${fps?.takeIf { it >= 50 }?.let { "$it" } ?: ""}")
                            else if (!note.isNullOrBlank()) add(note)
                            add(ext.uppercase(Locale.ROOT))
                        }
                        if (!codecTag.isNullOrBlank()) add(codecTag)
                        if (!isAudioOnly && !hasAudio) add("video-only")
                        if (!bitrateText.isNullOrBlank() && isAudioOnly) add(bitrateText)
                        if (!sizeText.isNullOrBlank()) add(sizeText)
                    }

                    val effectiveId = fmtId.ifBlank { "${ext}_${h ?: 0}_$i" }
                    val selector = if (fmtId.isNotBlank()) fmtId else (selectorForPreset(if (isAudioOnly) PRESET_AUDIO_ONLY else PRESET_BEST) ?: "best")

                    parsed.add(
                        MediaFormatOption(
                            formatId = effectiveId,
                            label = labelParts.joinToString(" • "),
                            ext = ext,
                            width = w,
                            height = h,
                            vcodec = vcodec,
                            acodec = acodec,
                            filesize = filesize,
                            tbr = tbr,
                            abr = abr,
                            fps = fps,
                            formatNote = note,
                            url = url,
                            isAudioOnly = isAudioOnly,
                            hasVideo = hasVideo,
                            hasAudio = hasAudio,
                            ytDlpSelector = selector
                        )
                    )
                }
            }
        }

        // Always append a convenience "Best Audio Only" choice for videos/streams if not already represented or helpful
        if ((item.isVideo || item.isStream) && parsed.none { it.formatId == PRESET_AUDIO_ONLY }) {
            val existingBestAudio = parsed.firstOrNull { it.isAudioOnly }
            parsed.add(
                MediaFormatOption(
                    formatId = PRESET_AUDIO_ONLY,
                    label = if (existingBestAudio != null) {
                        "Audio Only (Best • ${existingBestAudio.ext.uppercase(Locale.ROOT)})"
                    } else {
                        "Audio Only (M4A / Best Audio)"
                    },
                    ext = existingBestAudio?.ext ?: "m4a",
                    acodec = existingBestAudio?.acodec,
                    filesize = existingBestAudio?.filesize,
                    abr = existingBestAudio?.abr,
                    url = existingBestAudio?.url,
                    isAudioOnly = true,
                    hasVideo = false,
                    hasAudio = true,
                    ytDlpSelector = selectorForPreset(PRESET_AUDIO_ONLY)!!
                )
            )
        }

        if (parsed.isEmpty() && (item.isVideo || item.isStream)) {
            return listOf(
                MediaFormatOption(
                    formatId = PRESET_BEST,
                    label = "Best Available (${item.format.ifBlank { "MP4" }})",
                    ext = item.format.lowercase(Locale.ROOT).takeIf { it in setOf("mp4", "webm", "mkv", "mov") } ?: "mp4",
                    url = item.url,
                    isAudioOnly = false,
                    hasVideo = true,
                    hasAudio = true,
                    ytDlpSelector = "b[vcodec!=none][acodec!=none]/best"
                ),
                MediaFormatOption(
                    formatId = PRESET_1080P,
                    label = "1080p Full HD (MP4)",
                    ext = "mp4",
                    height = 1080,
                    url = null,
                    isAudioOnly = false,
                    hasVideo = true,
                    hasAudio = true,
                    ytDlpSelector = selectorForPreset(PRESET_1080P)!!
                ),
                MediaFormatOption(
                    formatId = PRESET_720P,
                    label = "720p HD (MP4)",
                    ext = "mp4",
                    height = 720,
                    url = null,
                    isAudioOnly = false,
                    hasVideo = true,
                    hasAudio = true,
                    ytDlpSelector = selectorForPreset(PRESET_720P)!!
                ),
                MediaFormatOption(
                    formatId = PRESET_480P,
                    label = "480p SD (MP4)",
                    ext = "mp4",
                    height = 480,
                    url = null,
                    isAudioOnly = false,
                    hasVideo = true,
                    hasAudio = true,
                    ytDlpSelector = selectorForPreset(PRESET_480P)!!
                ),
                MediaFormatOption(
                    formatId = PRESET_AUDIO_ONLY,
                    label = "Audio Only (M4A / Best Audio)",
                    ext = "m4a",
                    url = null,
                    isAudioOnly = true,
                    hasVideo = false,
                    hasAudio = true,
                    ytDlpSelector = selectorForPreset(PRESET_AUDIO_ONLY)!!
                )
            )
        }

        return parsed
    }

    fun resolveForPreset(item: ScrapedMediaItem, preset: String): MediaFormatOption? {
        if (preset == PRESET_BEST) return null
        val options = parseOptions(item)
        if (options.isEmpty()) return null

        return when (preset) {
            PRESET_AUDIO_ONLY -> {
                options.firstOrNull { it.isAudioOnly && it.url != null }
                    ?: options.firstOrNull { it.formatId == PRESET_AUDIO_ONLY }
            }
            PRESET_1080P, PRESET_720P, PRESET_480P -> {
                val maxH = when (preset) {
                    PRESET_1080P -> 1080
                    PRESET_720P -> 720
                    else -> 480
                }
                val muxedMatch = options
                    .filter { !it.isAudioOnly && it.hasVideo && it.hasAudio && (it.height ?: 0) in 1..maxH }
                    .maxByOrNull { it.height ?: 0 }
                muxedMatch ?: options.firstOrNull { it.formatId == preset }
                    ?: MediaFormatOption(
                        formatId = preset,
                        label = presetLabel(preset),
                        ext = "mp4",
                        height = maxH,
                        isAudioOnly = false,
                        hasVideo = true,
                        hasAudio = true,
                        ytDlpSelector = selectorForPreset(preset)!!
                    )
            }
            else -> null
        }
    }

    private fun formatCodecShortName(codec: String?): String? {
        val c = codec?.lowercase(Locale.ROOT)?.trim() ?: return null
        if (c == "none" || c.isBlank()) return null
        return when {
            c.startsWith("avc") || c.contains("h264") -> "H.264"
            c.startsWith("hev") || c.startsWith("hvc") || c.contains("265") -> "H.265"
            c.startsWith("vp9") || c.startsWith("vp09") -> "VP9"
            c.startsWith("av01") || c == "av1" -> "AV1"
            c.startsWith("mp4a") -> "AAC"
            c.startsWith("opus") -> "OPUS"
            c.startsWith("vorbis") -> "VORBIS"
            else -> c.substringBefore('.').uppercase(Locale.ROOT).take(8)
        }
    }
}
