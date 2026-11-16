package com.amresalehin.emregallery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emregallery.data.DownloaderSession
import com.amresalehin.emregallery.data.DualMediaExtractor
import com.amresalehin.emregallery.data.ExtractionProvider
import com.amresalehin.emregallery.data.GalleryDlEngine
import com.amresalehin.emregallery.data.MediaCaptureEngine
import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.MediaClassifier
import com.amresalehin.emregallery.data.MediaDeduplicator
import com.amresalehin.emregallery.data.MediaExtractionResult
import com.amresalehin.emregallery.data.MediaRequestContext
import com.amresalehin.emregallery.data.OriginalMediaResolver
import com.amresalehin.emregallery.data.ScrapedMediaItem
import com.amresalehin.emregallery.data.YtDlpProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaScraperFixesRegressionTest {

    @Test
    fun redditExternalPreviewIsNotRewrittenToIReddIt() {
        val extUrl = "https://external-preview.redd.it/abc123def.jpg?width=640&crop=smart&auto=webp&s=deadbeef"
        val resolved = OriginalMediaResolver.resolve(extUrl)
        assertEquals(extUrl, resolved.mediaUrl)
        assertNotEquals(
            MediaDeduplicator.deduplicationKey(extUrl),
            MediaDeduplicator.deduplicationKey("https://i.redd.it/abc123def.jpg")
        )

        val nativePreview = "https://preview.redd.it/native123.jpg?width=640&auto=webp&s=xyz"
        assertEquals("https://i.redd.it/native123.jpg", OriginalMediaResolver.resolve(nativePreview).mediaUrl)
    }

    @Test
    fun googleUserContentPreservesQueryTokensContainingEquals() {
        val sizedWithQuery = "https://lh3.googleusercontent.com/photoKey123=w400-h300-no?authuser=0&token=abc=def"
        val resolved1 = OriginalMediaResolver.resolve(sizedWithQuery)
        assertEquals("https://lh3.googleusercontent.com/photoKey123=s0?authuser=0&token=abc=def", resolved1.mediaUrl)

        val unsizedWithQuery = "https://lh3.googleusercontent.com/photoKey123?authuser=0&token=abc"
        val resolved2 = OriginalMediaResolver.resolve(unsizedWithQuery)
        assertEquals("https://lh3.googleusercontent.com/photoKey123=s0?authuser=0&token=abc", resolved2.mediaUrl)
    }

    @Test
    fun mediaClassifierInspectsPathAndFormatParamsInsteadOfSubstrings() {
        val jpgUnderMp4Folder = MediaClassifier.classify("https://cdn.example.com/mp4-thumb/cover.jpg")
        assertEquals(MediaCategory.IMAGES, jpgUnderMp4Folder.category)
        assertEquals("JPEG", jpgUnderMp4Folder.format)
        assertFalse(jpgUnderMp4Folder.isVideo)

        val mp4UnderPngFolder = MediaClassifier.classify("https://cdn.example.com/png-icons/clip.mp4")
        assertEquals(MediaCategory.VIDEOS, mp4UnderPngFolder.category)
        assertEquals("MP4", mp4UnderPngFolder.format)
        assertTrue(mp4UnderPngFolder.isVideo)

        val twitterPng = MediaClassifier.classify("https://pbs.twimg.com/media/F123456?format=png&name=orig")
        assertEquals(MediaCategory.IMAGES, twitterPng.category)
        assertEquals("PNG", twitterPng.format)

        val queryMp4 = MediaClassifier.classify("https://cdn.example.com/render?ref=thumb.jpg&format=mp4")
        assertEquals(MediaCategory.VIDEOS, queryMp4.category)
        assertEquals("MP4", queryMp4.format)
    }

    @Test
    fun mediaDeduplicatorPreservesMediaIdsInSAndFAndKeepsPngDistinctFromJpg() {
        assertNotEquals(
            MediaDeduplicator.deduplicationKey("https://example.com/media?s=12345"),
            MediaDeduplicator.deduplicationKey("https://example.com/media?s=67890")
        )
        assertNotEquals(
            MediaDeduplicator.deduplicationKey("https://example.com/media?f=clip1.mp4"),
            MediaDeduplicator.deduplicationKey("https://example.com/media?f=clip2.mp4")
        )
        assertNotEquals(
            MediaDeduplicator.deduplicationKey("https://example.com/gallery/photo.png"),
            MediaDeduplicator.deduplicationKey("https://example.com/gallery/photo.jpg")
        )
        // True sizing/format variants for s and f should still normalize
        assertEquals(
            MediaDeduplicator.deduplicationKey("https://example.com/avatar.jpg?s=400x400&f=webp"),
            MediaDeduplicator.deduplicationKey("https://example.com/avatar.jpg?s=thumb&f=jpg")
        )
    }

    @Test
    fun ytDlpSupportSkipsStaticImagesAndAcceptsStreamManifests() {
        assertTrue(GalleryDlEngine.checkYtDlpSupport("https://cdn.example.com/video/master.m3u8?token=1"))
        assertTrue(GalleryDlEngine.checkYtDlpSupport("https://cdn.example.com/video/manifest.mpd"))
        assertFalse(GalleryDlEngine.checkYtDlpSupport("https://cdn.example.com/photo.jpg"))

        val provider = YtDlpProvider()
        assertFalse(provider.isApplicable(MediaRequestContext.create("https://cdn.example.com/photo.jpg")))
        assertFalse(provider.isApplicable(MediaRequestContext.create("https://cdn.example.com/photo.png")))
    }

    @Test
    fun dualMediaExtractorPropagatesProviderFailuresWhenEmptyAndClearsErrorOnSuccess() = runBlocking {
        val ctx: Context = ApplicationProvider.getApplicationContext()

        val failingProvider = object : ExtractionProvider {
            override fun isApplicable(context: MediaRequestContext) = true
            override suspend fun extract(
                context: MediaRequestContext,
                onItemDiscovered: (ScrapedMediaItem) -> Unit,
                onStatusUpdate: (String) -> Unit
            ): MediaExtractionResult = MediaExtractionResult(
                items = emptyList(),
                errorMessage = "HTTP 403 Forbidden: login required"
            )
        }
        val throwingProvider = object : ExtractionProvider {
            override fun isApplicable(context: MediaRequestContext) = true
            override suspend fun extract(
                context: MediaRequestContext,
                onItemDiscovered: (ScrapedMediaItem) -> Unit,
                onStatusUpdate: (String) -> Unit
            ): MediaExtractionResult {
                throw IllegalStateException("Rate limited by host")
            }
        }

        val noOpDirect = object : ExtractionProvider {
            override fun isApplicable(context: MediaRequestContext) = false
            override suspend fun extract(
                context: MediaRequestContext,
                onItemDiscovered: (ScrapedMediaItem) -> Unit,
                onStatusUpdate: (String) -> Unit
            ) = MediaExtractionResult()
        }

        DownloaderSession.startSession("https://protected.example.com/post/1")
        val failingExtractor = DualMediaExtractor(
            context = ctx,
            directProviderFactory = { noOpDirect },
            concurrentProvidersFactory = {
                listOf(
                    "failing" to failingProvider,
                    "throwing" to throwingProvider
                )
            }
        )

        failingExtractor.extract("https://protected.example.com/post/1", this)
        val failState = withTimeout(5000) {
            failingExtractor.state.first { !it.isRunning }
        }

        assertFalse(failState.isRunning)
        assertEquals(DualMediaExtractor.ExtractionPhase.ERROR, failState.phase)
        assertNotNull(failState.errorMessage)
        assertTrue(failState.errorMessage!!.contains("403"))
        assertEquals(failState.errorMessage, DownloaderSession.errorMessage.value)

        // Partial success should clear errorMessage
        val succeedingProvider = object : ExtractionProvider {
            override fun isApplicable(context: MediaRequestContext) = true
            override suspend fun extract(
                context: MediaRequestContext,
                onItemDiscovered: (ScrapedMediaItem) -> Unit,
                onStatusUpdate: (String) -> Unit
            ): MediaExtractionResult {
                val item = ScrapedMediaItem(
                    id = "ok_1",
                    url = "https://cdn.example.com/video.mp4",
                    title = "video.mp4",
                    isVideo = true
                )
                onItemDiscovered(item)
                return MediaExtractionResult(items = listOf(item), pageTitle = "Found")
            }
        }

        DownloaderSession.startSession("https://protected.example.com/post/2")
        val partialSuccessExtractor = DualMediaExtractor(
            context = ctx,
            directProviderFactory = { noOpDirect },
            concurrentProvidersFactory = {
                listOf(
                    "failing" to failingProvider,
                    "succeeding" to succeedingProvider
                )
            }
        )
        partialSuccessExtractor.extract("https://protected.example.com/post/2", this)
        val okState = withTimeout(5000) {
            partialSuccessExtractor.state.first { !it.isRunning }
        }

        assertFalse(okState.isRunning)
        assertEquals(DualMediaExtractor.ExtractionPhase.COMPLETED, okState.phase)
        assertEquals(1, okState.items.size)
        assertNull(okState.errorMessage)
        assertNull(DownloaderSession.errorMessage.value)
    }

    @Test
    fun browserRuntimeScriptsIncludesFetchXhrMediaSourceAndBlobHooks() {
        val script = MediaCaptureEngine.getNetworkAndBlobHooksScript()
        assertTrue(script.contains("__emreNetworkHooksInstalled"))
        assertTrue(script.contains("window.fetch"))
        assertTrue(script.contains("XMLHttpRequest.prototype.open"))
        assertTrue(script.contains("XMLHttpRequest.prototype.send"))
        assertTrue(script.contains("MediaSource.prototype.addSourceBuffer"))
        assertTrue(script.contains("URL.createObjectURL"))
        assertTrue(script.contains("FileReader"))
        assertTrue(script.contains("EmreBridge.reportMedia"))
    }
}
