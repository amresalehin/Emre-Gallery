package com.amresalehin.emregallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val WIDTH_PRESETS = listOf(0, 300, 480, 720, 1280, 1920)
private val HEIGHT_PRESETS = listOf(0, 300, 480, 720, 1080)
private val SIZE_KB_PRESETS = listOf(0, 10, 50, 100, 500, 1024)

@Composable
fun DownloaderCategoryToolbar(
    categories: List<Triple<String, ImageVector, Int>>,
    selectedFilter: String?,
    onSelectFilter: (String?) -> Unit,
    sortOption: String,
    sortDropdownExpanded: Boolean,
    onSortDropdownExpandedChange: (Boolean) -> Unit,
    onSelectSortOption: (String) -> Unit,
    hasActiveDimensionFilter: Boolean,
    onToggleDimensionFilter: () -> Unit,
    isAllSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onOpenDownloadOptions: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(categories) { (categoryName, icon, count) ->
                val isSelected = selectedFilter == categoryName
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clickable {
                        onSelectFilter(if (isSelected) null else categoryName)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = categoryName,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "$count",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Spacer(Modifier.width(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    shape = CircleShape,
                    color = if (sortOption != "Default") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .size(34.dp)
                        .clickable { onSortDropdownExpandedChange(true) }
                        .testTag("downloader_sort_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Sort",
                            tint = if (sortOption != "Default") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = sortDropdownExpanded,
                    onDismissRequest = { onSortDropdownExpandedChange(false) }
                ) {
                    listOf("Default", "Title (A-Z)", "Videos First", "Images First", "Streams First").forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt, fontWeight = if (sortOption == opt) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                onSelectSortOption(opt)
                                onSortDropdownExpandedChange(false)
                            }
                        )
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = if (hasActiveDimensionFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .size(34.dp)
                    .clickable(onClick = onToggleDimensionFilter)
                    .testTag("downloader_filter_tune_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Filter dimensions & size",
                        tint = if (hasActiveDimensionFilter) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (onOpenDownloadOptions != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(onClick = onOpenDownloadOptions)
                        .testTag("downloader_options_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Download options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .size(34.dp)
                    .clickable(onClick = onToggleSelectAll)
                    .testTag("downloader_select_all_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isAllSelected) Icons.Default.Check else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = "Select All",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DownloaderDimensionFilterDrawer(
    visible: Boolean,
    minWidthFilter: Float,
    minHeightFilter: Float,
    showSvg: Boolean,
    onMinWidthChange: (Float) -> Unit,
    onMinHeightChange: (Float) -> Unit,
    onShowSvgChange: (Boolean) -> Unit,
    onResetFilters: () -> Unit,
    minKbInput: String = "1",
    onMinKbChange: (String) -> Unit = {}
) {
    val currentKb = (minKbInput.toFloatOrNull() ?: 0f).coerceIn(0f, 2048f)

    AnimatedVisibility(visible = visible) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .testTag("downloader_dimension_filter_drawer")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dimension & Size Filter",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = onResetFilters,
                        modifier = Modifier.testTag("downloader_filter_reset_btn")
                    ) {
                        Text("Reset", color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Min Width
                Text(
                    text = "Min Width: ${if (minWidthFilter <= 0f) "Any" else "${minWidthFilter.toInt()}px"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = minWidthFilter.coerceIn(0f, 3840f),
                    onValueChange = { raw ->
                        val snapped = ((raw / 10f).roundToInt() * 10f).coerceIn(0f, 3840f)
                        onMinWidthChange(snapped)
                    },
                    valueRange = 0f..3840f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("downloader_min_width_slider")
                )
                PresetThresholdRow(
                    presets = WIDTH_PRESETS,
                    activeValue = minWidthFilter.toInt(),
                    formatLabel = { if (it == 0) "Any" else "${it}px" },
                    onSelect = { onMinWidthChange(it.toFloat()) },
                    tagPrefix = "width_preset"
                )

                // Min Height
                Text(
                    text = "Min Height: ${if (minHeightFilter <= 0f) "Any" else "${minHeightFilter.toInt()}px"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = minHeightFilter.coerceIn(0f, 2160f),
                    onValueChange = { raw ->
                        val snapped = ((raw / 10f).roundToInt() * 10f).coerceIn(0f, 2160f)
                        onMinHeightChange(snapped)
                    },
                    valueRange = 0f..2160f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("downloader_min_height_slider")
                )
                PresetThresholdRow(
                    presets = HEIGHT_PRESETS,
                    activeValue = minHeightFilter.toInt(),
                    formatLabel = { if (it == 0) "Any" else "${it}px" },
                    onSelect = { onMinHeightChange(it.toFloat()) },
                    tagPrefix = "height_preset"
                )

                // Min File Size (KB)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Min File Size: ${if (currentKb <= 0f) "Any" else "${currentKb.toInt()} KB"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = minKbInput,
                        onValueChange = { raw ->
                            val filtered = raw.filter { it.isDigit() }.take(6)
                            onMinKbChange(filtered)
                        },
                        singleLine = true,
                        label = { Text("KB") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .width(96.dp)
                            .testTag("downloader_min_kb_input")
                    )
                }
                Slider(
                    value = currentKb,
                    onValueChange = { raw ->
                        val snapped = ((raw / 10f).roundToInt() * 10).coerceIn(0, 2048)
                        onMinKbChange(snapped.toString())
                    },
                    valueRange = 0f..2048f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("downloader_min_kb_slider")
                )
                PresetThresholdRow(
                    presets = SIZE_KB_PRESETS,
                    activeValue = minKbInput.toIntOrNull() ?: 0,
                    formatLabel = {
                        when {
                            it == 0 -> "Any"
                            it >= 1024 -> "${it / 1024} MB"
                            else -> "${it} KB"
                        }
                    },
                    onSelect = { onMinKbChange(it.toString()) },
                    tagPrefix = "size_kb_preset"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Show SVG images",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = showSvg,
                        onCheckedChange = onShowSvgChange,
                        modifier = Modifier.testTag("downloader_show_svg_switch")
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetThresholdRow(
    presets: List<Int>,
    activeValue: Int,
    formatLabel: (Int) -> String,
    onSelect: (Int) -> Unit,
    tagPrefix: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        presets.forEach { preset ->
            FilterChip(
                selected = activeValue == preset,
                onClick = { onSelect(preset) },
                label = {
                    Text(
                        text = formatLabel(preset),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier.testTag("${tagPrefix}_$preset")
            )
        }
    }
}
