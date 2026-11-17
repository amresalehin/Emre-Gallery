package com.amresalehin.emregallery.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.amresalehin.emregallery.data.preferences.DownloaderPreferences
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

/**
 * Manages persistent media downloads through Android Jetpack WorkManager.
 * Schedules, tracks, and cancels background downloads with network constraints.
 * Keys unique work by deduped URL and synchronizes active work IDs with running jobs.
 */
object DownloadQueueManager {

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private val _inMemoryDownloading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val inMemoryDownloading: kotlinx.coroutines.flow.StateFlow<Boolean> = _inMemoryDownloading

    private val activeDownloadCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val activeItemIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun isDownloading(itemId: String): Boolean {
        if (activeItemIds.contains(itemId)) return true
        val activity = DownloadActivityManager.activities.value.firstOrNull { it.id == itemId }
        return activity?.status == DownloadActivityStatus.DOWNLOADING || activity?.status == DownloadActivityStatus.RETRYING
    }

    fun markDownloadStarted(count: Int = 1, itemId: String? = null) {
        if (itemId != null) activeItemIds.add(itemId)
        activeDownloadCount.addAndGet(count)
        _inMemoryDownloading.value = true
    }

    fun markDownloadCompleted(itemId: String? = null) {
        if (itemId != null) activeItemIds.remove(itemId)
        val remaining = activeDownloadCount.decrementAndGet()
        if (remaining <= 0) {
            activeDownloadCount.set(0)
            _inMemoryDownloading.value = false
        }
    }

    private fun serializeHeaders(headers: Map<String, String>): String? {
        if (headers.isEmpty()) return null
        return runCatching {
            val json = JSONObject()
            headers.forEach { (k, v) ->
                // Guard against exceeding the 10KB WorkManager Data limit by capping individual header values
                if (v.length <= 2048) {
                    json.put(k, v)
                }
            }
            json.toString().takeIf { it.length <= 4096 }
        }.getOrNull()
    }

    private data class ResolvedDownloadPlan(
        val effectiveItem: ScrapedMediaItem,
        val renderedFileName: String,
        val formatSpec: String?,
        val webpageUrl: String?,
        val targetSubfolder: String?
    )

    private fun resolveDownloadPlan(
        context: Context,
        item: ScrapedMediaItem,
        selectedFormat: MediaFormatOption?,
        sourceDomain: String,
        referer: String?,
        qualityPresetOverride: String?,
        filenameTemplateOverride: String?,
        subfolderByDomainOverride: Boolean?
    ): ResolvedDownloadPlan {
        val activePreset = qualityPresetOverride ?: DownloaderPreferences.getPreferredQualityPreset(context)
        val activeTemplate = filenameTemplateOverride ?: DownloaderPreferences.getFilenameTemplate(context)
        val useSubfolder = subfolderByDomainOverride ?: DownloaderPreferences.isSubfolderByDomainEnabled(context)

        val chosenFormat = selectedFormat ?: if (item.isVideo || item.isStream) {
            MediaFormatSelectionHelper.resolveForPreset(item, activePreset)
        } else {
            null
        }

        val resolvedUrl = chosenFormat?.url?.takeIf { it.startsWith("http", ignoreCase = true) } ?: item.url
        val resolvedIsAudio = chosenFormat?.isAudioOnly ?: item.isAudio
        val resolvedIsVideo = if (chosenFormat != null) !chosenFormat.isAudioOnly else item.isVideo
        val resolvedFormatLabel = chosenFormat?.ext?.uppercase() ?: item.format

        val effectiveExt = chosenFormat?.ext?.takeIf { it.isNotBlank() }
            ?: DownloadFilenameFormatter.inferExtension(
                title = item.title,
                format = resolvedFormatLabel,
                url = resolvedUrl,
                isAudio = resolvedIsAudio,
                isVideo = resolvedIsVideo
            )

        val effectiveDomain = DownloadFilenameFormatter.sanitizeDomainFolder(
            sourceDomain.ifBlank { item.metadata["webpage_url"] ?: referer ?: item.url }
        )

        val renderedName = DownloadFilenameFormatter.formatFileName(
            template = activeTemplate,
            title = item.title,
            ext = effectiveExt,
            uploader = item.metadata["uploader"],
            uploadDate = item.metadata["upload_date"],
            mediaId = item.metadata["media_id"] ?: item.id,
            domain = effectiveDomain
        )

        val updatedItem = item.copy(
            url = resolvedUrl,
            isVideo = resolvedIsVideo,
            isAudio = resolvedIsAudio,
            format = resolvedFormatLabel
        )

        val webpageUrl = item.metadata["webpage_url"]
            ?: item.provenance.pageUrl.takeIf { it.isNotBlank() }
            ?: referer

        val formatSpec = when {
            chosenFormat == null -> null
            // If format already resolved to a direct non-manifest CDN URL, yt-dlp isn't strictly required unless webpage download is desired
            chosenFormat.url != null &&
                !chosenFormat.url.contains(".m3u8", ignoreCase = true) &&
                !chosenFormat.url.contains(".mpd", ignoreCase = true) -> null
            else -> chosenFormat.ytDlpSelector
        }

        return ResolvedDownloadPlan(
            effectiveItem = updatedItem,
            renderedFileName = renderedName,
            formatSpec = formatSpec,
            webpageUrl = webpageUrl,
            targetSubfolder = if (useSubfolder) effectiveDomain else null
        )
    }

    fun enqueueDownload(
        context: Context,
        item: ScrapedMediaItem,
        referer: String? = null,
        stackId: String? = null,
        stackTitle: String? = null,
        sourceDomain: String? = null,
        sourceUrl: String? = null,
        isRetry: Boolean = false,
        selectedFormat: MediaFormatOption? = null,
        filenameTemplateOverride: String? = null,
        subfolderByDomainOverride: Boolean? = null,
        qualityPresetOverride: String? = null
    ): UUID {
        val sId = stackId ?: "stack_${System.currentTimeMillis()}"
        val sTitle = stackTitle ?: sourceDomain ?: "Discovered Media"
        val domain = sourceDomain ?: ""
        val url = sourceUrl ?: item.url

        val plan = resolveDownloadPlan(
            context = context,
            item = item,
            selectedFormat = selectedFormat,
            sourceDomain = domain,
            referer = referer,
            qualityPresetOverride = qualityPresetOverride,
            filenameTemplateOverride = filenameTemplateOverride,
            subfolderByDomainOverride = subfolderByDomainOverride
        )
        val effItem = plan.effectiveItem
        val headersJson = serializeHeaders(effItem.downloadHeaders)

        val inputData = Data.Builder()
            .putString(MediaDownloadWorker.KEY_MEDIA_URL, effItem.url)
            .putString(MediaDownloadWorker.KEY_FILE_NAME, plan.renderedFileName)
            .putBoolean(MediaDownloadWorker.KEY_USE_RENDERED_FILENAME, true)
            .putString(MediaDownloadWorker.KEY_REFERER, referer)
            .putString(MediaDownloadWorker.KEY_ITEM_ID, effItem.id)
            .putString(MediaDownloadWorker.KEY_TITLE, effItem.title)
            .putBoolean(MediaDownloadWorker.KEY_IS_VIDEO, effItem.isVideo)
            .putBoolean(MediaDownloadWorker.KEY_IS_AUDIO, effItem.isAudio)
            .putBoolean(MediaDownloadWorker.KEY_IS_STREAM, effItem.isStream)
            .putString(MediaDownloadWorker.KEY_FORMAT, effItem.format)
            .putString(MediaDownloadWorker.KEY_STACK_ID, sId)
            .putString(MediaDownloadWorker.KEY_STACK_TITLE, sTitle)
            .putString(MediaDownloadWorker.KEY_SOURCE_DOMAIN, domain)
            .putString(MediaDownloadWorker.KEY_SOURCE_URL, url)
            .putString(MediaDownloadWorker.KEY_SOURCE_ENGINE, effItem.provenance.extractorEngine)
            .apply {
                if (!headersJson.isNullOrBlank()) {
                    putString(MediaDownloadWorker.KEY_HEADERS_JSON, headersJson)
                }
                if (!plan.formatSpec.isNullOrBlank()) {
                    putString(MediaDownloadWorker.KEY_FORMAT_SPEC, plan.formatSpec)
                }
                if (!plan.webpageUrl.isNullOrBlank()) {
                    putString(MediaDownloadWorker.KEY_WEBPAGE_URL, plan.webpageUrl)
                }
                if (!plan.targetSubfolder.isNullOrBlank()) {
                    putString(MediaDownloadWorker.KEY_TARGET_SUBFOLDER, plan.targetSubfolder)
                }
            }
            .build()

        val request = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
            .setConstraints(networkConstraints)
            .setInputData(inputData)
            .addTag(MediaDownloadWorker.WORK_TAG_DOWNLOAD)
            .addTag("item_${effItem.id}")
            .build()

        val dedupKey = MediaDeduplicator.deduplicationKey(effItem.url) + (plan.formatSpec ?: "")
        val uniqueWorkName = "download_${abs(dedupKey.hashCode())}"

        val workManager = WorkManager.getInstance(context)
        val existingWorkList = runCatching {
            workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
        }.getOrDefault(emptyList())

        val activeWork = existingWorkList.firstOrNull {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
        }

        val finalWorkId: UUID
        if (activeWork != null && !isRetry) {
            finalWorkId = activeWork.id
            activeItemIds.add(effItem.id)
        } else {
            finalWorkId = request.id
            workManager.enqueueUniqueWork(
                uniqueWorkName,
                if (isRetry) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
            markDownloadStarted(count = 1, itemId = effItem.id)
        }

        DownloadActivityManager.initialize(context)
        DownloadActivityManager.recordQueued(
            id = effItem.id,
            title = plan.renderedFileName,
            mediaUrl = effItem.url,
            thumbnailUrl = effItem.thumbnailUrl ?: effItem.url,
            isVideo = effItem.isVideo,
            isAudio = effItem.isAudio,
            isStream = effItem.isStream,
            format = effItem.format,
            workId = finalWorkId.toString(),
            isRetry = isRetry,
            stackId = sId,
            stackTitle = sTitle,
            sourceDomain = domain,
            sourceUrl = url
        )

        return finalWorkId
    }

    fun enqueueBatch(
        context: Context,
        items: List<ScrapedMediaItem>,
        referer: String? = null,
        stackId: String? = null,
        stackTitle: String? = null,
        sourceDomain: String? = null,
        sourceUrl: String? = null,
        selectedFormats: Map<String, MediaFormatOption> = emptyMap(),
        filenameTemplateOverride: String? = null,
        subfolderByDomainOverride: Boolean? = null,
        qualityPresetOverride: String? = null
    ): List<UUID> {
        val sId = stackId ?: "stack_${System.currentTimeMillis()}"
        val sTitle = stackTitle ?: sourceDomain ?: "Discovered Media"
        val domain = sourceDomain ?: ""
        val url = sourceUrl ?: referer ?: ""

        val workManager = WorkManager.getInstance(context)
        val distinctItems = MediaDeduplicator.deduplicateScrapedItems(items)

        val resolvedPlans = distinctItems.map { item ->
            resolveDownloadPlan(
                context = context,
                item = item,
                selectedFormat = selectedFormats[item.id],
                sourceDomain = domain,
                referer = referer,
                qualityPresetOverride = qualityPresetOverride,
                filenameTemplateOverride = filenameTemplateOverride,
                subfolderByDomainOverride = subfolderByDomainOverride
            )
        }

        val workIds = resolvedPlans.map { plan ->
            val item = plan.effectiveItem
            val headersJson = serializeHeaders(item.downloadHeaders)
            val inputData = Data.Builder()
                .putString(MediaDownloadWorker.KEY_MEDIA_URL, item.url)
                .putString(MediaDownloadWorker.KEY_FILE_NAME, plan.renderedFileName)
                .putBoolean(MediaDownloadWorker.KEY_USE_RENDERED_FILENAME, true)
                .putString(MediaDownloadWorker.KEY_REFERER, referer)
                .putString(MediaDownloadWorker.KEY_ITEM_ID, item.id)
                .putString(MediaDownloadWorker.KEY_TITLE, item.title)
                .putBoolean(MediaDownloadWorker.KEY_IS_VIDEO, item.isVideo)
                .putBoolean(MediaDownloadWorker.KEY_IS_AUDIO, item.isAudio)
                .putBoolean(MediaDownloadWorker.KEY_IS_STREAM, item.isStream)
                .putString(MediaDownloadWorker.KEY_FORMAT, item.format)
                .putString(MediaDownloadWorker.KEY_STACK_ID, sId)
                .putString(MediaDownloadWorker.KEY_STACK_TITLE, sTitle)
                .putString(MediaDownloadWorker.KEY_SOURCE_DOMAIN, domain)
                .putString(MediaDownloadWorker.KEY_SOURCE_URL, url)
                .putString(MediaDownloadWorker.KEY_SOURCE_ENGINE, item.provenance.extractorEngine)
                .apply {
                    if (!headersJson.isNullOrBlank()) {
                        putString(MediaDownloadWorker.KEY_HEADERS_JSON, headersJson)
                    }
                    if (!plan.formatSpec.isNullOrBlank()) {
                        putString(MediaDownloadWorker.KEY_FORMAT_SPEC, plan.formatSpec)
                    }
                    if (!plan.webpageUrl.isNullOrBlank()) {
                        putString(MediaDownloadWorker.KEY_WEBPAGE_URL, plan.webpageUrl)
                    }
                    if (!plan.targetSubfolder.isNullOrBlank()) {
                        putString(MediaDownloadWorker.KEY_TARGET_SUBFOLDER, plan.targetSubfolder)
                    }
                }
                .build()

            val request = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
                .setConstraints(networkConstraints)
                .setInputData(inputData)
                .addTag(MediaDownloadWorker.WORK_TAG_DOWNLOAD)
                .addTag("item_${item.id}")
                .build()

            val dedupKey = MediaDeduplicator.deduplicationKey(item.url) + (plan.formatSpec ?: "")
            val uniqueWorkName = "download_${abs(dedupKey.hashCode())}"

            val existingWorkList = runCatching {
                workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
            }.getOrDefault(emptyList())

            val activeWork = existingWorkList.firstOrNull {
                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
            }

            if (activeWork != null) {
                activeItemIds.add(item.id)
                activeWork.id
            } else {
                workManager.enqueueUniqueWork(
                    uniqueWorkName,
                    ExistingWorkPolicy.KEEP,
                    request
                )
                markDownloadStarted(count = 1, itemId = item.id)
                request.id
            }
        }

        DownloadActivityManager.initialize(context)
        DownloadActivityManager.recordBatchQueued(
            items = resolvedPlans.map { it.effectiveItem.copy(title = it.renderedFileName) },
            workIds = workIds,
            stackId = sId,
            stackTitle = sTitle,
            sourceDomain = domain,
            sourceUrl = url
        )

        return workIds
    }

    fun getActiveDownloadsFlow(context: Context): Flow<List<WorkInfo>> {
        return WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(MediaDownloadWorker.WORK_TAG_DOWNLOAD)
    }

    fun cancelDownload(context: Context, workId: UUID) {
        runCatching {
            WorkManager.getInstance(context).cancelWorkById(workId)
        }
        markDownloadCompleted()
    }

    fun cancelAll(context: Context) {
        runCatching {
            WorkManager.getInstance(context).cancelAllWorkByTag(MediaDownloadWorker.WORK_TAG_DOWNLOAD)
        }
        activeItemIds.clear()
        activeDownloadCount.set(0)
        _inMemoryDownloading.value = false
    }
}
