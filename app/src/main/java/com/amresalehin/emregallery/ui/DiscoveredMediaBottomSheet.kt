package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.MediaFormatOption
import com.amresalehin.emregallery.data.MediaFormatSelectionHelper
import com.amresalehin.emregallery.data.ScrapedMediaItem
import com.amresalehin.emregallery.data.preferences.DownloaderPreferences

/**
 * Modal bottom sheet presenting media discovered during in-app web browsing,
 * with category filtering, per-item quality/audio format selection, download options, and batch enqueue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoveredMediaBottomSheet(
    filterOptions: List<String>,
    discoveredItems: List<ScrapedMediaItem>,
    filteredItems: List<ScrapedMediaItem>,
    selectedFilter: String?,
    selectedItemIds: Set<String>,
    selectedFormats: Map<String, MediaFormatOption> = emptyMap(),
    isDownloading: Boolean,
    downloadProgress: Float,
    onToggleFilter: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onItemSelectionChange: (String, Boolean) -> Unit,
    onSelectItemFormat: ((ScrapedMediaItem) -> Unit)? = null,
    onOpenDownloadSettings: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onDownloadSelected: () -> Unit
) {
    val context = LocalContext.current
    DownloaderPreferences.ensureInitialized(context)
    val preferredQuality by DownloaderPreferences.preferredQualityPreset.collectAsState()
    val subfolderByDomain by DownloaderPreferences.subfolderByDomain.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("discovered_media_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val isAllSelected = filteredItems.isNotEmpty() &&
                selectedItemIds.containsAll(filteredItems.map { it.id }.toSet())

            DiscoveredMediaFilterBar(
                filterOptions = filterOptions,
                discoveredItems = discoveredItems,
                selectedFilter = selectedFilter,
                isAllSelected = isAllSelected,
                onToggleFilter = onToggleFilter,
                onToggleSelectAll = onToggleSelectAll
            )

            if (onOpenDownloadSettings != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildString {
                            append("Quality: ")
                            append(MediaFormatSelectionHelper.presetLabel(preferredQuality))
                            if (subfolderByDomain) append(" • Subfolder: On")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onOpenDownloadSettings,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("discovered_download_options_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Download options",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Options", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (filteredItems.isEmpty()) {
                Text(
                    text = "No matching media discovered on this page yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        val isSelected = item.id in selectedItemIds
                        DiscoveredMediaItemRow(
                            item = item,
                            isSelected = isSelected,
                            selectedFormat = selectedFormats[item.id],
                            onSelectFormat = onSelectItemFormat?.let { cb -> { cb(item) } },
                            onSelectionChange = { selected ->
                                onItemSelectionChange(item.id, selected)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onDownloadSelected,
                enabled = selectedItemIds.isNotEmpty() && !isDownloading,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("discovered_download_selected_btn")
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        progress = { downloadProgress },
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Downloading (${(downloadProgress * 100).toInt()}%)...")
                } else {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download (${selectedItemIds.size}) to Inbox")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
