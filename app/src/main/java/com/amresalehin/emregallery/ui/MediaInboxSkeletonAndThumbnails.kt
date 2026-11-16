package com.amresalehin.emregallery.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.MediaInboxItem

/**
 * True Mosaic Thumbnail component for media stacks.
 * Supports:
 * - 0 items: Empty placeholder
 * - 1 item: Full image
 * - 2 items: 2 equal vertical columns (50% / 50%) side-by-side
 * - 3 items: 1 tall on left (58%), 2 stacked on right (42%)
 * - 4+ items: 2x2 quadrant grid with +N counter on the 4th quadrant
 */
@Composable
fun TrueMediaMosaicThumbnail(
    items: List<MediaInboxItem>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        when {
            items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            items.size == 1 -> {
                AsyncImage(
                    model = items[0].localUri,
                    contentDescription = items[0].title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            items.size == 2 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AsyncImage(
                        model = items[0].localUri,
                        contentDescription = items[0].title,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                    AsyncImage(
                        model = items[1].localUri,
                        contentDescription = items[1].title,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            items.size == 3 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AsyncImage(
                        model = items[0].localUri,
                        contentDescription = items[0].title,
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AsyncImage(
                            model = items[1].localUri,
                            contentDescription = items[1].title,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentScale = ContentScale.Crop
                        )
                        AsyncImage(
                            model = items[2].localUri,
                            contentDescription = items[2].title,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AsyncImage(
                            model = items[0].localUri,
                            contentDescription = items[0].title,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                        AsyncImage(
                            model = items[1].localUri,
                            contentDescription = items[1].title,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AsyncImage(
                            model = items[2].localUri,
                            contentDescription = items[2].title,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            AsyncImage(
                                model = items[3].localUri,
                                contentDescription = items[3].title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (items.size > 4) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.58f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${items.size - 3}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Single skeleton card displayed when media is actively downloading.
 */
@Composable
fun DownloadingSkeletonCard(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "download_skeleton_shimmer")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "download_skeleton_alpha"
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("downloading_skeleton_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alphaAnim)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "Downloading media",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = alphaAnim * 0.7f),
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = alphaAnim * 0.5f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = alphaAnim * 0.35f))
                )
            }
        }
    }
}

/**
 * Skeleton wireframe grid placeholder.
 */
@Composable
fun SkeletonWireframeGrid() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SkeletonCard(heightDp = 130)
            SkeletonCard(heightDp = 190)
            SkeletonCard(heightDp = 130)
            SkeletonCard(heightDp = 160)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SkeletonCard(heightDp = 190)
            SkeletonCard(heightDp = 120)
            SkeletonCard(heightDp = 170)
            SkeletonCard(heightDp = 120)
        }
    }
}

/**
 * Single skeleton card placeholder.
 */
@Composable
fun SkeletonCard(heightDp: Int) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                )
            }
        }
    }
}
