package com.amresalehin.emregallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Full-screen in-app Chromium browser launched from the Media Downloader page.
 * Allows web browsing and auto-detects downloadable media on any visited site.
 */
@Composable
fun FullScreenBrowserView(
    initialUrl: String = "",
    onClose: () -> Unit,
    onDownloadComplete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val controller = rememberFullScreenBrowserController(
        initialUrl = initialUrl,
        onDownloadComplete = onDownloadComplete
    )

    BackHandler {
        when {
            controller.formatPickerItem != null -> controller.formatPickerItem = null
            controller.showDownloadSettingsDialog -> controller.showDownloadSettingsDialog = false
            controller.showDiscoveredMediaSheet -> controller.showDiscoveredMediaSheet = false
            else -> onClose()
        }
    }

    Surface(
        modifier = modifier.testTag("full_screen_browser_view"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            DirectBrowserView(
                initialUrl = controller.currentUrl,
                discoveredCount = controller.discoveredItems.size,
                onClose = onClose,
                onUrlChange = controller::onNavigatedUrl,
                onPageTitleChanged = { controller.pageTitle = it },
                onMediaExtracted = controller::onMediaExtracted,
                onBatchExtracted = { batch, title, _ ->
                    controller.onBatchExtracted(batch, title)
                },
                onSwitchToMediaTab = {
                    controller.showDiscoveredMediaSheet = true
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (controller.showDiscoveredMediaSheet) {
            DiscoveredMediaBottomSheet(
                filterOptions = BROWSER_MEDIA_FILTER_OPTIONS,
                discoveredItems = controller.discoveredItems,
                filteredItems = controller.filteredItems,
                selectedFilter = controller.selectedFilter,
                selectedItemIds = controller.selectedItemIds,
                selectedFormats = controller.selectedFormats,
                isDownloading = controller.isDownloading,
                downloadProgress = controller.downloadProgress,
                onToggleFilter = controller::toggleFilter,
                onToggleSelectAll = controller::toggleSelectAllFiltered,
                onItemSelectionChange = controller::setItemSelected,
                onSelectItemFormat = { item -> controller.formatPickerItem = item },
                onOpenDownloadSettings = { controller.showDownloadSettingsDialog = true },
                onDismiss = { controller.showDiscoveredMediaSheet = false },
                onDownloadSelected = controller::downloadSelected
            )
        }

        controller.formatPickerItem?.let { item ->
            MediaFormatPickerDialog(
                item = item,
                selectedOption = controller.selectedFormats[item.id],
                onSelectOption = { opt ->
                    controller.setFormatForItem(item.id, opt)
                    controller.formatPickerItem = null
                },
                onDismiss = { controller.formatPickerItem = null }
            )
        }

        if (controller.showDownloadSettingsDialog) {
            DownloaderSettingsDialog(
                onDismiss = { controller.showDownloadSettingsDialog = false }
            )
        }
    }
}
