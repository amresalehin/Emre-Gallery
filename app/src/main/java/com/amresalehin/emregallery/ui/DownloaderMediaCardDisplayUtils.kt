package com.amresalehin.emregallery.ui

import com.amresalehin.emregallery.data.DownloadFilenameFormatter
import com.amresalehin.emregallery.data.MediaFormatOption
import com.amresalehin.emregallery.data.ScrapedMediaItem

internal data class ScrapedCardDisplayMeta(
    val titleText: String,
    val dimText: String,
    val categoryText: String,
    val formatText: String,
    val durationText: String? = null
)

internal fun resolveScrapedCardMeta(
    item: ScrapedMediaItem,
    dynamicDimensions: Map<String, Pair<Int, Int>>,
    selectedFormat: MediaFormatOption? = null
): ScrapedCardDisplayMeta {
    val sanitizedResHint = item.resolutionHint
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "40 × 40" && it != "40x40" }

    val itemForDimParse = if (sanitizedResHint == null && !item.resolutionHint.isNullOrBlank()) {
        item.copy(resolutionHint = null)
    } else {
        item
    }
    val parsedDim = dynamicDimensions[item.id] ?: parseScrapedMediaDimensions(itemForDimParse).let { (w, h) ->
        if (w != null && h != null) (w to h) else null
    }
    val durationText = DownloadFilenameFormatter.formatDurationSeconds(
        item.durationSeconds ?: item.metadata["duration"]?.toLongOrNull()
    )

    val dimText = when {
        selectedFormat?.width != null && selectedFormat.height != null ->
            "${selectedFormat.width} × ${selectedFormat.height}"
        parsedDim != null && parsedDim.first > 0 && parsedDim.second > 0 ->
            "${parsedDim.first} × ${parsedDim.second}"
        sanitizedResHint != null ->
            sanitizedResHint
        item.sizeBytes != null && item.sizeBytes > 0L ->
            DownloadFilenameFormatter.formatFileSize(item.sizeBytes).orEmpty()
        else -> ""
    }

    val categoryText = when {
        selectedFormat?.isAudioOnly == true -> "Audio"
        item.isVideo -> "Videos"
        item.isAudio -> "Audio"
        item.isStream -> "Streams"
        else -> "Images"
    }

    val formatText = when {
        selectedFormat != null -> {
            when {
                selectedFormat.isAudioOnly -> "AUDIO • ${selectedFormat.ext.uppercase()}"
                selectedFormat.height != null -> "${selectedFormat.height}p"
                else -> selectedFormat.ext.uppercase()
            }
        }
        !item.isVideo && item.format.contains("4K", ignoreCase = true) -> "JPG"
        else -> {
            val raw = item.format.uppercase().trim()
            when {
                raw == "JPEG" -> "JPG"
                raw.isNotBlank() -> raw
                item.isVideo -> "MP4"
                item.isAudio -> "MP3"
                item.isStream -> "M3U8"
                else -> "JPG"
            }
        }
    }

    return ScrapedCardDisplayMeta(
        titleText = item.title.ifBlank { "media_${item.id.takeLast(6)}" },
        dimText = dimText,
        categoryText = categoryText,
        formatText = formatText,
        durationText = durationText
    )
}
