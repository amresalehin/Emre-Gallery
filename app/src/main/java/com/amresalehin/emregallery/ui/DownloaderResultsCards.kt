package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.MediaFormatOption
import com.amresalehin.emregallery.data.ScrapedMediaItem

/**
 * Clean, theme-adaptive media card for scraped downloader results.
 * Displays format/quality pill top-left (interactive for video/stream items),
 * selection checkbox top-right, duration hint bottom-right of thumbnail,
 * filename below thumbnail, dimensions/size bottom-left, and category bottom-right.
 */
@Composable
fun DownloaderMediaCard(
    item: ScrapedMediaItem,
    isSelected: Boolean,
    dynamicDimensions: Map<String, Pair<Int, Int>>,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isDownloaded: Boolean = false,
    selectedFormat: MediaFormatOption? = null,
    onFormatBadgeClick: (() -> Unit)? = null
) {
    val meta = resolveScrapedCardMeta(item, dynamicDimensions, selectedFormat)
    val canPickFormat = onFormatBadgeClick != null &&
        (item.isVideo || item.isStream || !item.metadata["formats_json"].isNullOrBlank())

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("scraped_media_card_${item.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DownloaderMediaCardThumbnail(
                previewUrl = item.thumbnailUrl ?: item.url,
                contentDescription = item.title,
                formatText = meta.formatText,
                durationText = meta.durationText,
                isTicked = isSelected || isDownloaded,
                isDownloaded = isDownloaded,
                canPickFormat = canPickFormat,
                onFormatBadgeClick = onFormatBadgeClick,
                itemId = item.id
            )

            DownloaderMediaCardFooter(
                titleText = meta.titleText,
                dimText = meta.dimText,
                categoryText = meta.categoryText
            )
        }
    }
}

@Composable
private fun DownloaderMediaCardThumbnail(
    previewUrl: String,
    contentDescription: String,
    formatText: String,
    durationText: String?,
    isTicked: Boolean,
    isDownloaded: Boolean,
    canPickFormat: Boolean,
    onFormatBadgeClick: (() -> Unit)?,
    itemId: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        AsyncImage(
            model = previewUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            shadowElevation = 1.dp,
            modifier = Modifier
                .padding(start = 6.dp, top = 6.dp)
                .align(Alignment.TopStart)
                .then(
                    if (canPickFormat && onFormatBadgeClick != null) {
                        Modifier
                            .clickable(onClick = onFormatBadgeClick)
                            .testTag("card_format_badge_$itemId")
                    } else {
                        Modifier
                    }
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = formatText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (canPickFormat) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Choose quality or audio format",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        if (!durationText.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                modifier = Modifier
                    .padding(end = 6.dp, bottom = 6.dp)
                    .align(Alignment.BottomEnd)
                    .testTag("card_duration_badge_$itemId")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.size(9.dp)
                    )
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(end = 6.dp, top = 6.dp)
                .align(Alignment.TopEnd)
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isTicked) MaterialTheme.colorScheme.primary
                    else Color.Black.copy(alpha = 0.35f)
                )
                .border(
                    width = 1.5.dp,
                    color = if (isTicked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isTicked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = if (isDownloaded) "Downloaded" else "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun DownloaderMediaCardFooter(
    titleText: String,
    dimText: String,
    categoryText: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = titleText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dimText,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = categoryText,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
