package com.amresalehin.emregallery.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.MediaInboxStack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val StackLayerPalettesLight = listOf(
    listOf(Color(0xFFEDE9FE), Color(0xFFDDD6FE)),
    listOf(Color(0xFFFEF3C7), Color(0xFFFFD1D8)),
    listOf(Color(0xFFD1FAE5), Color(0xFFA7F3D0)),
    listOf(Color(0xFFFCE7F3), Color(0xFFFBCFE8)),
    listOf(Color(0xFFCFFAFE), Color(0xFFA5F3FC)),
    listOf(Color(0xFFE0E7FF), Color(0xFFC7D2FE))
)

private val StackLayerPalettesDark = listOf(
    listOf(Color(0xFF312E81).copy(alpha = 0.65f), Color(0xFF3730A3).copy(alpha = 0.45f)),
    listOf(Color(0xFF78350F).copy(alpha = 0.65f), Color(0xFF92400E).copy(alpha = 0.45f)),
    listOf(Color(0xFF064E3B).copy(alpha = 0.65f), Color(0xFF065F46).copy(alpha = 0.5f)),
    listOf(Color(0xFF831843).copy(alpha = 0.65f), Color(0xFF9D174D).copy(alpha = 0.45f)),
    listOf(Color(0xFF164E63).copy(alpha = 0.65f), Color(0xFF155E75).copy(alpha = 0.45f)),
    listOf(Color(0xFF1E1B4B).copy(alpha = 0.65f), Color(0xFF312E81).copy(alpha = 0.45f))
)

/**
 * Image Stack Card displaying a hero cover thumbnail, stacked offset layers,
 * source favicon, collection title, and item count.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageStackCard(
    stack: MediaInboxStack,
    accentIndex: Int = 0,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val layerColors = remember(accentIndex, isDark) {
        val list = if (isDark) StackLayerPalettesDark else StackLayerPalettesLight
        list[accentIndex % list.size]
    }

    val dateStr = remember(stack.timestampMillis) {
        SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(stack.timestampMillis))
    }

    val coverItem = remember(stack.items) {
        stack.items.firstOrNull { !it.isVideo && it.localUri.isNotBlank() }
            ?: stack.items.firstOrNull { it.localUri.isNotBlank() }
            ?: stack.items.firstOrNull()
    }
    val coverImageModel = remember(coverItem) {
        coverItem?.localUri?.takeIf { it.isNotBlank() }
            ?: coverItem?.sourceUrl?.takeIf { it.isNotBlank() }
    }

    val cardShape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 8.dp, bottom = 8.dp)
            .testTag("inbox_image_stack_${stack.id}")
    ) {
        // Layer 2 (Backmost stacked card - offset 8dp bottom-right)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 8.dp, y = 8.dp)
                .background(layerColors[1], shape = cardShape)
                .border(1.dp, layerColors[1].copy(alpha = 0.85f), shape = cardShape)
        )

        // Layer 1 (Middle stacked card - offset 4dp bottom-right)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(layerColors[0], shape = cardShape)
                .border(1.dp, layerColors[0].copy(alpha = 0.9f), shape = cardShape)
        )

        // Front Main Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) {
                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, shape = cardShape)
                    } else Modifier
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = if (isDark) 1.dp else 3.dp,
            border = BorderStroke(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.45f)
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Single Hero Thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(165.dp)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    if (coverImageModel != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(coverImageModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = stack.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    if (isSelectionMode) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f),
                            border = BorderStroke(
                                2.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                            ),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .size(26.dp)
                                .testTag("stack_selection_check_${stack.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Information Section beneath the cover image
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val cleanDomain = remember(stack.sourceDomain, stack.sourceUrl) {
                            val raw = stack.sourceDomain.ifBlank {
                                try {
                                    Uri.parse(stack.sourceUrl).host.orEmpty()
                                } catch (_: Exception) { "" }
                            }
                            raw.removePrefix("www.").lowercase().trim()
                        }
                        val faviconUrl = remember(cleanDomain) {
                            if (cleanDomain.isNotBlank()) "https://www.google.com/s2/favicons?domain=$cleanDomain&sz=64" else null
                        }

                        var faviconFailed by remember(faviconUrl) { mutableStateOf(false) }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                            modifier = Modifier.size(17.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (!faviconFailed && faviconUrl != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(faviconUrl)
                                            .crossfade(true)
                                            .listener(onError = { _, _ -> faviconFailed = true })
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    val initial = cleanDomain.firstOrNull()?.uppercaseChar()?.toString()
                                        ?: stack.title.firstOrNull()?.uppercaseChar()?.toString()
                                        ?: "W"
                                    Text(
                                        text = initial,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Text(
                            text = stack.title.ifBlank { stack.sourceDomain.ifBlank { "Saved Collection" } },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "$dateStr • ${stack.items.size} ${if (stack.items.size == 1) "item" else "items"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }
    }
}
