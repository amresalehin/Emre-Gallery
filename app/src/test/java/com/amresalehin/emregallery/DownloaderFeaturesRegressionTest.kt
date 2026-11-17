package com.amresalehin.emregallery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emregallery.data.CapturedMediaFactory
import com.amresalehin.emregallery.data.DownloadFilenameFormatter
import com.amresalehin.emregallery.data.DownloaderSession
import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.MediaDownloadStorage
import com.amresalehin.emregallery.data.MediaFormatOption
import com.amresalehin.emregallery.data.MediaFormatSelectionHelper
import com.amresalehin.emregallery.data.ScrapedMediaItem
import com.amresalehin.emregallery.data.preferences.DownloaderPreferences
import com.amresalehin.emregallery.ui.FullScreenBrowserController
import com.amresalehin.emregallery.ui.filterAndSortScrapedResults
import com.amresalehin.emregallery.ui.resolveScrapedCardMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloaderFeaturesRegressionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DownloaderSession.resetDiscoveredForNavigation("")
        DownloaderPreferences.setFilenameTemplate(context, DownloadFilenameFormatter.DEFAULT_FILENAME_TEMPLATE)
        DownloaderPreferences.setSubfolderByDomain(context, false)
        DownloaderPreferences.setPreferredQualityPreset(context, MediaFormatSelectionHelper.PRESET_BEST)
    }

    @Test
    fun browserNavigationClearsStaleDiscoveredItemsWhilePreservingSamePageReloadAndHashes() {
        val controller = FullScreenBrowserController(
            initialUrl = "https://www.example.com/gallery/1",
            context = context,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            onDownloadComplete = {}
        )

        val item = ScrapedMediaItem(
            id = "img_1",
            url = "https://cdn.example.com/photo1.jpg",
            title = "photo1.jpg",
            format = "JPG",
            category = MediaCategory.IMAGES
        )
        controller.onBatchExtracted(listOf(item), "Gallery One")
        controller.autoSelectIfNeeded()
        controller.toggleFilter("Images")
        controller.setFormatForItem(
            item.id,
            MediaFormatOption(formatId = "720", label = "720p", ext = "mp4", height = 720)
        )

        assertEquals(1, controller.discoveredItems.size)
        assertEquals(setOf("img_1"), controller.selectedItemIds)
        assertEquals("Images", controller.selectedFilter)
        assertNotNull(controller.selectedFormats[item.id])

        // Navigating to a fragment on the same page should NOT wipe discovered items
        controller.onNavigatedUrl("https://example.com/gallery/1/#comments")
        assertEquals(1, controller.discoveredItems.size)

        // Navigating to a new page MUST clear stale discoveries, selections, filters, formats, and title
        controller.onNavigatedUrl("https://example.com/gallery/2")
        assertTrue(controller.discoveredItems.isEmpty())
        assertTrue(controller.selectedItemIds.isEmpty())
        assertNull(controller.selectedFilter)
        assertTrue(controller.selectedFormats.isEmpty())
        assertEquals("", controller.pageTitle)
        assertTrue(DownloaderSession.discoveredItems.value.isEmpty())

        // Re-adding and going Home ("") must also clear discovered items
        controller.onMediaExtracted(item)
        assertEquals(1, controller.discoveredItems.size)
        controller.onNavigatedUrl("")
        assertTrue(controller.discoveredItems.isEmpty())
        assertEquals("", controller.currentUrl)
    }

    @Test
    fun mediaFormatSelectionHelperParsesVideoAndAudioFormatsAndResolvesPresets() {
        val formatsArray = JSONArray().apply {
            put(JSONObject().apply {
                put("format_id", "22")
                put("ext", "mp4")
                put("width", 1280)
                put("height", 720)
                put("fps", 30)
                put("vcodec", "avc1.64001F")
                put("acodec", "mp4a.40.2")
                put("tbr", 1500.0)
                put("filesize", 15_000_000L)
                put("has_video", true)
                put("has_audio", true)
                put("is_audio_only", false)
                put("url", "https://cdn.example.com/v720.mp4")
            })
            put(JSONObject().apply {
                put("format_id", "18")
                put("ext", "mp4")
                put("width", 640)
                put("height", 360)
                put("vcodec", "avc1.42001E")
                put("acodec", "mp4a.40.2")
                put("has_video", true)
                put("has_audio", true)
                put("is_audio_only", false)
                put("url", "https://cdn.example.com/v360.mp4")
            })
            put(JSONObject().apply {
                put("format_id", "140")
                put("ext", "m4a")
                put("acodec", "mp4a.40.2")
                put("abr", 128.0)
                put("filesize", 2_500_000L)
                put("has_video", false)
                put("has_audio", true)
                put("is_audio_only", true)
                put("url", "https://cdn.example.com/audio.m4a")
            })
        }

        val videoItem = ScrapedMediaItem(
            id = "vid_1",
            url = "https://cdn.example.com/v720.mp4",
            title = "Highlight Clip.mp4",
            isVideo = true,
            category = MediaCategory.VIDEOS,
            format = "MP4",
            metadata = mapOf("formats_json" to formatsArray.toString())
        )

        val options = MediaFormatSelectionHelper.parseOptions(videoItem)
        assertTrue(options.any { it.formatId == "22" && it.label.contains("720p") && it.label.contains("H.264") })
        assertTrue(options.any { it.formatId == "18" && it.height == 360 })
        assertTrue(options.any { it.formatId == "140" && it.isAudioOnly && it.ext == "m4a" })

        val preset720 = MediaFormatSelectionHelper.resolveForPreset(videoItem, MediaFormatSelectionHelper.PRESET_720P)
        assertNotNull(preset720)
        assertEquals("22", preset720?.formatId)

        val preset480 = MediaFormatSelectionHelper.resolveForPreset(videoItem, MediaFormatSelectionHelper.PRESET_480P)
        assertNotNull(preset480)
        assertEquals("18", preset480?.formatId)

        val presetAudio = MediaFormatSelectionHelper.resolveForPreset(videoItem, MediaFormatSelectionHelper.PRESET_AUDIO_ONLY)
        assertNotNull(presetAudio)
        assertTrue(presetAudio!!.isAudioOnly)
        assertEquals("m4a", presetAudio.ext)
    }

    @Test
    fun filenameFormatterAndDomainSubfolderStorageWorkEndToEnd() {
        val renderedFull = DownloadFilenameFormatter.formatFileName(
            template = "%(upload_date)s - %(uploader)s - %(title)s [%(id)s]",
            title = "Launch Trailer: Part 1?.mp4",
            ext = "mp4",
            uploader = "Studio/Official",
            uploadDate = "20260401",
            mediaId = "vid_99",
            domain = "www.youtube.com"
        )
        assertEquals("20260401 - Studio_Official - Launch Trailer_ Part 1 [vid_99].mp4", renderedFull)

        val renderedMissingOptional = DownloadFilenameFormatter.formatFileName(
            template = "%(upload_date)s - %(uploader)s - %(title)s [%(id)s]",
            title = "Solo Clip.mp4",
            ext = "mp4",
            uploader = null,
            uploadDate = null,
            mediaId = null
        )
        assertEquals("Solo Clip.mp4", renderedMissingOptional)

        assertEquals("vimeo.com", DownloadFilenameFormatter.sanitizeDomainFolder("https://www.vimeo.com/12345?foo=1"))

        val targets = MediaDownloadStorage.resolveDownloadTargets(
            context = context,
            mediaUrl = "https://cdn.example.com/clip.mp4",
            fileName = renderedFull,
            downloadId = "vid_99",
            domainSubfolder = "https://www.example.com/watch?v=1",
            useRenderedFileName = true
        )
        assertEquals("example.com", targets.finalFile.parentFile?.name)
        assertEquals(renderedFull, targets.finalFile.name)
        assertEquals("vid_99.mp4.downloading", targets.tempFile.name)
        assertEquals("vid_99.mp4.meta", targets.metaFile.name)
    }

    @Test
    fun cardMetaNeverShowsMisleading40x40AndFormatsVideoDurationAndSize() {
        val unknownDimsVideo = ScrapedMediaItem(
            id = "v_unknown",
            url = "https://cdn.example.com/stream.mp4",
            title = "Demo Reel.mp4",
            isVideo = true,
            category = MediaCategory.VIDEOS,
            format = "MP4",
            resolutionHint = null,
            sizeBytes = 5_242_880L,
            durationSeconds = 225L
        )
        val meta = resolveScrapedCardMeta(unknownDimsVideo, emptyMap())
        assertFalse(meta.dimText.contains("40"))
        assertEquals("5.0 MB", meta.dimText)
        assertEquals("03:45", meta.durationText)

        // Unknown dimensions + unknown size returns blank instead of fake "40 × 40"
        val noMetaImage = ScrapedMediaItem(
            id = "i_nometa",
            url = "https://cdn.example.com/photo",
            title = "photo",
            category = MediaCategory.IMAGES,
            resolutionHint = null,
            sizeBytes = null
        )
        assertEquals("", resolveScrapedCardMeta(noMetaImage, emptyMap()).dimText)

        // Explicit stale "40 × 40" placeholder hint is also stripped to blank
        val emptyImage = noMetaImage.copy(id = "i_empty", resolutionHint = "40 × 40")
        val emptyMeta = resolveScrapedCardMeta(emptyImage, emptyMap())
        assertNotEquals("40 × 40", emptyMeta.dimText)
        assertEquals("", emptyMeta.dimText)

        // Selected Audio-Only format reflects in category and format badge
        val audioChoice = MediaFormatOption(
            formatId = "audio_only",
            label = "Audio Only",
            ext = "m4a",
            isAudioOnly = true,
            hasVideo = false,
            hasAudio = true
        )
        val audioMeta = resolveScrapedCardMeta(unknownDimsVideo, emptyMap(), selectedFormat = audioChoice)
        assertEquals("Audio", audioMeta.categoryText)
        assertEquals("AUDIO • M4A", audioMeta.formatText)

        // JsDomScanScript payload parsing extracts durationSeconds and strips placeholder "video" resolution hint
        val domJson = JSONArray().apply {
            put(JSONObject().apply {
                put("u", "https://cdn.example.com/clip.mp4")
                put("s", "DOM")
                put("r", "video")
                put("d", 132L)
            })
        }.toString()
        val parsedDom = CapturedMediaFactory.parseJsDomScanResult(domJson, "https://example.com")
        assertEquals(1, parsedDom.size)
        assertEquals(132L, parsedDom[0].durationSeconds)
        assertNull(parsedDom[0].resolutionHint)
    }

    @Test
    fun fineGrainedDimensionThresholdsAndMinKbSizeFilterWorkCorrectly() {
        val smallImg = ScrapedMediaItem(
            id = "small",
            url = "https://cdn.example.com/300x300.jpg",
            title = "thumb.jpg",
            category = MediaCategory.IMAGES,
            resolutionHint = "300x300",
            sizeBytes = 25 * 1024L
        )
        val hdImg = ScrapedMediaItem(
            id = "hd",
            url = "https://cdn.example.com/1280x720.jpg",
            title = "banner.jpg",
            category = MediaCategory.IMAGES,
            resolutionHint = "1280x720",
            sizeBytes = 250 * 1024L
        )
        val fullHdImg = ScrapedMediaItem(
            id = "fhd",
            url = "https://cdn.example.com/1920x1080.jpg",
            title = "hero.jpg",
            category = MediaCategory.IMAGES,
            resolutionHint = "1920x1080",
            sizeBytes = 800 * 1024L
        )

        val all = listOf(smallImg, hdImg, fullHdImg)

        // Intermediate threshold 480px width excludes 300px, keeps 1280px & 1920px
        val minWidth480 = filterAndSortScrapedResults(
            allItems = all,
            selectedFilter = null,
            sortOption = "Default",
            minWidthFilter = 480f,
            minHeightFilter = 0f,
            showSvg = false,
            minKbInput = "0",
            dynamicDimensions = emptyMap()
        )
        assertEquals(listOf("hd", "fhd"), minWidth480.filteredItems.map { it.id })

        // Intermediate threshold 720px width + 720px height
        val min720p = filterAndSortScrapedResults(
            allItems = all,
            selectedFilter = null,
            sortOption = "Default",
            minWidthFilter = 1280f,
            minHeightFilter = 720f,
            showSvg = false,
            minKbInput = "0",
            dynamicDimensions = emptyMap()
        )
        assertEquals(listOf("hd", "fhd"), min720p.filteredItems.map { it.id })

        // Size filter 500 KB excludes 25 KB and 250 KB items, keeps 800 KB item
        val min500Kb = filterAndSortScrapedResults(
            allItems = all,
            selectedFilter = null,
            sortOption = "Default",
            minWidthFilter = 0f,
            minHeightFilter = 0f,
            showSvg = false,
            minKbInput = "500",
            dynamicDimensions = emptyMap()
        )
        assertEquals(listOf("fhd"), min500Kb.filteredItems.map { it.id })
    }
}
