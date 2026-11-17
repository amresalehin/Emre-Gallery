package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.DownloadFilenameFormatter
import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.MediaFormatOption
import com.amresalehin.emregallery.data.ScrapedMediaItem

/**
 * Selectable media row displayed inside the browser's discovered media bottom sheet,
 * including duration hint and per-item quality/format selection for video/audio/streams.
 */
@Composable
internal fun DiscoveredMediaItemRow(
    item: ScrapedMediaItem,
    isSelected: Boolean,
    selectedFormat: MediaFormatOption? = null,
    onSelectFormat: (() -> Unit)? = null,
    onSelectionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val durationText = DownloadFilenameFormatter.formatDurationSeconds(
        item.durationSeconds ?: item.metadata["duration"]?.toLongOrNull()
    )
    val canChooseFormat = item.isVideo || item.isStream || !item.metadata["formats_json"].isNullOrBlank()
    val formatChipLabel = when {
        selectedFormat?.isAudioOnly == true -> "Audio (${selectedFormat.ext.uppercase()})"
        selectedFormat?.height != null -> "${selectedFormat.height}p ${selectedFormat.ext.uppercase()}"
        selectedFormat != null -> selectedFormat.ext.uppercase()
        else -> item.format
    }

    Surface(
        onClick = { onSelectionChange(!isSelected) },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(48.dp)
            ) {
                if (item.category == MediaCategory.IMAGES) {
                    AsyncImage(
                        model = item.thumbnailUrl ?: item.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = when {
                                item.isStream -> Icons.Default.LiveTv
                                item.isVideo -> Icons.Default.Videocam
                                item.isAudio -> Icons.Default.Audiotrack
                                else -> Icons.Default.Image
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val detailsText = buildList {
                    add(item.category.label)
                    add(formatChipLabel)
                    if (durationText != null) add(durationText)
                    item.resolutionHint?.takeIf { it.isNotBlank() && it != "40 × 40" }?.let { add(it) }
                }.joinToString(" • ")
                Text(
                    text = detailsText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (canChooseFormat && onSelectFormat != null) {
                Surface(
                    onClick = onSelectFormat,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.testTag("discovered_item_format_btn_${item.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedFormat != null) formatChipLabel else "Quality",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Choose quality or audio format",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChange
            )
        }
    }
}
