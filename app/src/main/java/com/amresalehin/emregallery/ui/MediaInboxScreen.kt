package com.amresalehin.emregallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.work.WorkInfo
import com.amresalehin.emregallery.data.DownloadQueueManager
import com.amresalehin.emregallery.data.MediaInboxRepository
import com.amresalehin.emregallery.data.MediaInboxStack
import kotlinx.coroutines.launch

enum class DownloaderViewMode {
    SEARCH_PAGE,
    DOWNLOADER_PAGE,
    SAVED_LIBRARY,
    ACTIVITY_PAGE
}

@Composable
fun MediaInboxScreen(
    onBack: () -> Unit,
    onOpenMediaItem: ((String) -> Unit)? = null,
    onOpenMediaWithList: ((String, List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val inboxRepo = remember { MediaInboxRepository.getInstance(context) }
    val stacks by inboxRepo.stacks.collectAsState()
    val workInfos by remember(context) {
        DownloadQueueManager.getActiveDownloadsFlow(context)
    }.collectAsState(initial = emptyList())
    val isDownloading = remember(workInfos) {
        workInfos.any {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedStackForDetail by remember { mutableStateOf<MediaInboxStack?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showFullScreenBrowser by remember { mutableStateOf(false) }
    var browserInitialUrl by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf("Newest") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedStackIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedStackIds.isNotEmpty()
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

    var currentViewMode by remember { mutableStateOf(DownloaderViewMode.SEARCH_PAGE) }
    var activeFetchUrl by remember { mutableStateOf("") }

    BackHandler(enabled = !showFullScreenBrowser) {
        if (isSelectionMode) {
            selectedStackIds = emptySet()
        } else if (isSearchFocused || searchQuery.isNotEmpty()) {
            searchQuery = ""
            isSearchFocused = false
            focusManager.clearFocus()
            keyboardController?.hide()
        } else if (currentViewMode == DownloaderViewMode.DOWNLOADER_PAGE) {
            currentViewMode = DownloaderViewMode.SEARCH_PAGE
        } else if (currentViewMode == DownloaderViewMode.ACTIVITY_PAGE) {
            currentViewMode = DownloaderViewMode.SEARCH_PAGE
        } else if (selectedStackForDetail != null) {
            selectedStackForDetail = null
        } else if (showFilterSheet) {
            showFilterSheet = false
        } else if (currentViewMode == DownloaderViewMode.SAVED_LIBRARY) {
            currentViewMode = DownloaderViewMode.SEARCH_PAGE
        } else {
            onBack()
        }
    }

    val filteredStacks = remember(stacks, searchQuery, sortOrder) {
        val base = if (searchQuery.isBlank()) {
            stacks
        } else {
            stacks.filter { stack ->
                stack.title.contains(searchQuery, ignoreCase = true) ||
                    stack.sourceDomain.contains(searchQuery, ignoreCase = true) ||
                    stack.items.any { it.title.contains(searchQuery, ignoreCase = true) }
            }
        }
        if (sortOrder == "Newest") {
            base.sortedByDescending { it.timestampMillis }
        } else {
            base.sortedBy { it.timestampMillis }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("media_inbox_screen")
    ) {
        if (currentViewMode == DownloaderViewMode.DOWNLOADER_PAGE && activeFetchUrl.isNotBlank()) {
            DownloaderResultsPage(
                targetUrl = activeFetchUrl,
                onBack = { currentViewMode = DownloaderViewMode.SEARCH_PAGE },
                onOpenBrowser = { url ->
                    browserInitialUrl = url
                    showFullScreenBrowser = true
                },
                onDownloadComplete = { /* Silent foreground download with no toast */ },
                onViewSavedLibrary = {
                    currentViewMode = DownloaderViewMode.SAVED_LIBRARY
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    if (currentViewMode == DownloaderViewMode.SAVED_LIBRARY) {
                        val allSelected = filteredStacks.isNotEmpty() && filteredStacks.all { it.id in selectedStackIds }
                        MediaInboxTopBar(
                            isSelectionMode = isSelectionMode,
                            selectedCount = selectedStackIds.size,
                            allSelected = allSelected,
                            currentViewMode = currentViewMode,
                            searchQuery = searchQuery,
                            isSearchFocused = isSearchFocused,
                            onExitSelection = { selectedStackIds = emptySet() },
                            onToggleSelectAll = {
                                selectedStackIds = if (allSelected) {
                                    emptySet()
                                } else {
                                    filteredStacks.map { it.id }.toSet()
                                }
                            },
                            onDeleteSelected = { showDeleteConfirmationDialog = true },
                            onSearchQueryChange = { searchQuery = it },
                            onSearchFocusChange = { isSearchFocused = it },
                            onClearSearchFocus = {
                                searchQuery = ""
                                isSearchFocused = false
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            },
                            onHideKeyboard = { keyboardController?.hide() },
                            onSwitchViewMode = { currentViewMode = it },
                            onShowFilterSheet = { showFilterSheet = true }
                        )
                    }
                },
                floatingActionButton = {
                    if (currentViewMode == DownloaderViewMode.SAVED_LIBRARY && !isSelectionMode) {
                        FloatingActionButton(
                            onClick = { currentViewMode = DownloaderViewMode.SEARCH_PAGE },
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                            modifier = Modifier
                                .padding(bottom = 16.dp, end = 8.dp)
                                .size(56.dp)
                                .testTag("media_inbox_fab_add")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Media Download",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentViewMode) {
                        DownloaderViewMode.SEARCH_PAGE -> {
                            DownloaderSearchMainPage(
                                onFetchUrl = { targetUrl ->
                                    activeFetchUrl = targetUrl
                                    currentViewMode = DownloaderViewMode.DOWNLOADER_PAGE
                                },
                                onOpenBrowser = { targetUrl ->
                                    browserInitialUrl = targetUrl
                                    showFullScreenBrowser = true
                                },
                                onViewSavedLibrary = {
                                    currentViewMode = DownloaderViewMode.SAVED_LIBRARY
                                },
                                onOpenActivity = {
                                    currentViewMode = DownloaderViewMode.ACTIVITY_PAGE
                                },
                                onBack = onBack,
                                savedStacks = stacks,
                                onSelectStack = { stack ->
                                    selectedStackForDetail = stack
                                },
                                activeDownloadsCount = workInfos.count {
                                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                                },
                                savedStacksCount = stacks.size,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        DownloaderViewMode.ACTIVITY_PAGE -> {
                            DownloaderActivityPage(
                                onBack = { currentViewMode = DownloaderViewMode.SEARCH_PAGE },
                                onAddNewDownload = { currentViewMode = DownloaderViewMode.SEARCH_PAGE },
                                onOpenMedia = { item ->
                                    item.localUri?.let { uriStr ->
                                        onOpenMediaItem?.invoke(uriStr)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            SavedLibraryGridContent(
                                filteredStacks = filteredStacks,
                                isDownloading = isDownloading,
                                selectedStackIds = selectedStackIds,
                                isSelectionMode = isSelectionMode,
                                onStackClick = { stack, isSelected ->
                                    if (isSelectionMode) {
                                        selectedStackIds = if (isSelected) {
                                            selectedStackIds - stack.id
                                        } else {
                                            selectedStackIds + stack.id
                                        }
                                    } else {
                                        selectedStackForDetail = stack
                                    }
                                },
                                onStackLongClick = { stack, isSelected ->
                                    selectedStackIds = if (!isSelectionMode) {
                                        setOf(stack.id)
                                    } else if (isSelected) {
                                        selectedStackIds - stack.id
                                    } else {
                                        selectedStackIds + stack.id
                                    }
                                },
                                onDeleteStack = { stack ->
                                    inboxRepo.deleteStack(stack.id)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Stack removed from Inbox")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showDeleteConfirmationDialog) {
            InboxDeleteConfirmationDialog(
                selectedCount = selectedStackIds.size,
                onConfirm = {
                    val idsToDelete = selectedStackIds.toList()
                    showDeleteConfirmationDialog = false
                    selectedStackIds = emptySet()
                    idsToDelete.forEach { id -> inboxRepo.deleteStack(id) }
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (idsToDelete.size == 1) "1 stack deleted" else "${idsToDelete.size} stacks deleted"
                        )
                    }
                },
                onDismiss = { showDeleteConfirmationDialog = false }
            )
        }

        if (showFilterSheet) {
            InboxOptionsBottomSheet(
                sortOrder = sortOrder,
                hasStacks = stacks.isNotEmpty(),
                onSortOrderChange = { sortOrder = it },
                onClearAll = {
                    inboxRepo.clearAll()
                    showFilterSheet = false
                    scope.launch { snackbarHostState.showSnackbar("All inbox stacks cleared") }
                },
                onDismiss = { showFilterSheet = false }
            )
        }

        AnimatedVisibility(
            visible = selectedStackForDetail != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(20f)
        ) {
            selectedStackForDetail?.let { stack ->
                MediaStackDetailTab(
                    stack = stack,
                    onClose = { selectedStackForDetail = null },
                    onOpenMedia = { uri ->
                        val allUris = stack.items.map { it.localUri.ifBlank { it.sourceUrl } }
                        if (onOpenMediaWithList != null) {
                            onOpenMediaWithList(uri, allUris)
                        } else {
                            onOpenMediaItem?.invoke(uri)
                        }
                    },
                    onOpenBrowser = { url ->
                        browserInitialUrl = url
                        showFullScreenBrowser = true
                    },
                    onDeleteStack = {
                        inboxRepo.deleteStack(stack.id)
                        selectedStackForDetail = null
                        scope.launch {
                            snackbarHostState.showSnackbar("Stack removed from Inbox")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        AnimatedVisibility(
            visible = showFullScreenBrowser,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(25f)
        ) {
            FullScreenBrowserView(
                initialUrl = browserInitialUrl,
                onClose = {
                    showFullScreenBrowser = false
                    browserInitialUrl = ""
                },
                onDownloadComplete = { count ->
                    scope.launch {
                        snackbarHostState.showSnackbar("Downloaded $count media items to Inbox!")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SavedLibraryGridContent(
    filteredStacks: List<MediaInboxStack>,
    isDownloading: Boolean,
    selectedStackIds: Set<String>,
    isSelectionMode: Boolean,
    onStackClick: (MediaInboxStack, Boolean) -> Unit,
    onStackLongClick: (MediaInboxStack, Boolean) -> Unit,
    onDeleteStack: (MediaInboxStack) -> Unit
) {
    if (filteredStacks.isNotEmpty() || isDownloading) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 20.dp)
                .testTag("inbox_media_grid"),
            contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isDownloading) {
                item(key = "downloading_skeleton_card_item") {
                    DownloadingSkeletonCard()
                }
            }
            itemsIndexed(filteredStacks, key = { _, stack -> stack.id }) { index, stack ->
                val isSelected = stack.id in selectedStackIds
                ImageStackCard(
                    stack = stack,
                    accentIndex = index,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
                    onClick = { onStackClick(stack, isSelected) },
                    onLongClick = { onStackLongClick(stack, isSelected) },
                    onDelete = { onDeleteStack(stack) }
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("inbox_empty_state")
        )
    }
}
