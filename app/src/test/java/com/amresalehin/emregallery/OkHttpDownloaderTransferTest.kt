package com.amresalehin.emregallery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emregallery.data.MediaDownloadWorker
import com.amresalehin.emregallery.data.OkHttpDownloader
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OkHttpDownloaderTransferTest {

    @Test
    fun testWorkManagerDownloadWorkerConstants() {
        assertEquals("media_download", MediaDownloadWorker.WORK_TAG_DOWNLOAD)
        assertEquals("media_url", MediaDownloadWorker.KEY_MEDIA_URL)
        assertEquals("file_name", MediaDownloadWorker.KEY_FILE_NAME)
        assertEquals("stack_id", MediaDownloadWorker.KEY_STACK_ID)
        assertEquals("progress", MediaDownloadWorker.KEY_PROGRESS)
    }

    @Test
    fun testMockWebServerResumableRange206() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val context: Context = ApplicationProvider.getApplicationContext()
            val downloadId = "test_resumable_video"
            val targetDir = OkHttpDownloader.getTargetStorageDir(context)
            val tempFile = File(targetDir, "$downloadId.mp4.downloading")
            tempFile.writeBytes(ByteArray(5) { 0x41.toByte() })

            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "video/mp4")
                    .setHeader("Content-Range", "bytes 5-9/10")
                    .setHeader("Content-Length", "5")
                    .setBody(Buffer().write(ByteArray(5) { 0x42.toByte() }))
            )

            val url = server.url("/video.mp4").toString()
            val result = OkHttpDownloader.downloadMediaFileDetailed(
                context = context,
                mediaUrl = url,
                fileName = "video.mp4",
                downloadId = downloadId
            )

            val recorded = server.takeRequest()
            assertEquals("bytes=5-", recorded.getHeader("Range"))
            assertTrue(result.isSuccessful)
            assertTrue(result.isResumed)
            assertEquals(10L, result.bytesDownloaded)
            assertNotNull(result.file)
            assertEquals(10L, result.file?.length())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun testMockWebServerHttp200FallbackClearsPartialFile() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val context: Context = ApplicationProvider.getApplicationContext()
            val downloadId = "test_fallback_video"
            val targetDir = OkHttpDownloader.getTargetStorageDir(context)
            val tempFile = File(targetDir, "$downloadId.mp4.downloading")
            tempFile.writeBytes(ByteArray(8) { 0x55.toByte() })

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "video/mp4")
                    .setBody(Buffer().write(ByteArray(10) { 0x77.toByte() }))
                    .setHeader("Content-Length", "10")
            )

            val url = server.url("/video.mp4").toString()
            val result = OkHttpDownloader.downloadMediaFileDetailed(
                context = context,
                mediaUrl = url,
                fileName = "video.mp4",
                downloadId = downloadId
            )

            assertTrue(result.isSuccessful)
            assertFalse(result.isResumed)
            assertEquals(10L, result.bytesDownloaded)
            assertNotNull(result.file)
            assertEquals(10L, result.file?.length())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun testMockWebServerHttp416SafeHandling() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val context: Context = ApplicationProvider.getApplicationContext()
            val downloadId = "test_416_video"
            val targetDir = OkHttpDownloader.getTargetStorageDir(context)
            val tempFile = File(targetDir, "$downloadId.mp4.downloading")
            tempFile.writeBytes(ByteArray(50) { 0x11.toByte() })

            server.enqueue(
                MockResponse()
                    .setResponseCode(416)
                    .setHeader("Content-Range", "bytes */10")
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "video/mp4")
                    .setBody(Buffer().write(ByteArray(10) { 0x22.toByte() }))
                    .setHeader("Content-Length", "10")
            )

            val url = server.url("/video.mp4").toString()
            val result = OkHttpDownloader.downloadMediaFileDetailed(
                context = context,
                mediaUrl = url,
                fileName = "video.mp4",
                downloadId = downloadId
            )

            assertTrue(result.isSuccessful)
            assertEquals(10L, result.bytesDownloaded)
            assertNotNull(result.file)
            assertEquals(10L, result.file?.length())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun testMockWebServerContentRangeMismatchRestartsCleanly() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val context: Context = ApplicationProvider.getApplicationContext()
            val downloadId = "test_mismatch_resume"
            val targetDir = OkHttpDownloader.getTargetStorageDir(context)
            val tempFile = File(targetDir, "$downloadId.mp4.downloading")
            tempFile.writeBytes(ByteArray(10) { 0x11.toByte() })

            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "video/mp4")
                    .setHeader("Content-Range", "bytes 0-19/20")
                    .setBody(Buffer().write(ByteArray(20) { 0x22.toByte() }))
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "video/mp4")
                    .setBody(Buffer().write(ByteArray(20) { 0x33.toByte() }))
                    .setHeader("Content-Length", "20")
            )

            val url = server.url("/video.mp4").toString()
            val result = OkHttpDownloader.downloadMediaFileDetailed(
                context = context,
                mediaUrl = url,
                fileName = "video.mp4",
                downloadId = downloadId
            )

            assertTrue(result.isSuccessful)
            assertEquals(20L, result.bytesDownloaded)
            assertNotNull(result.file)
            assertEquals(20L, result.file?.length())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun testMockWebServerHttp416AlreadyCompleteCommitsFile() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val context: Context = ApplicationProvider.getApplicationContext()
            val downloadId = "test_416_complete"
            val targetDir = OkHttpDownloader.getTargetStorageDir(context)
            val tempFile = File(targetDir, "$downloadId.mp4.downloading")
            tempFile.writeBytes(ByteArray(30) { 0x44.toByte() })

            server.enqueue(
                MockResponse()
                    .setResponseCode(416)
                    .setHeader("Content-Range", "bytes */30")
            )

            val url = server.url("/video.mp4").toString()
            val result = OkHttpDownloader.downloadMediaFileDetailed(
                context = context,
                mediaUrl = url,
                fileName = "video.mp4",
                downloadId = downloadId
            )

            assertTrue(result.isSuccessful)
            assertEquals(30L, result.bytesDownloaded)
            assertNotNull(result.file)
            assertEquals(30L, result.file?.length())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun testMockWebServerInterruptedTransferRejected() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val context: Context = ApplicationProvider.getApplicationContext()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "video/mp4")
                    .setBody(Buffer().write(ByteArray(15) { 0x33.toByte() }))
                    .setHeader("Content-Length", "50")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END)
            )

            val url = server.url("/video.mp4").toString()
            val result = OkHttpDownloader.downloadMediaFileDetailed(context, url, "video.mp4")
            assertFalse(result.isSuccessful)
            assertNull(result.file)
            assertTrue(
                result.errorMessage?.contains("interrupted") == true ||
                    result.errorMessage?.contains("incomplete") == true ||
                    result.errorMessage?.contains("size mismatch") == true ||
                    result.errorMessage?.contains("stream") == true ||
                    result.errorMessage?.contains("unexpected") == true ||
                    result.errorMessage?.contains("timeout") == true
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun testSupportedNonRasterFormatsValidation() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val context: Context = ApplicationProvider.getApplicationContext()
            val nonRasterFormats = listOf(
                Pair("audio/aac", "song.aac"),
                Pair("audio/flac", "music.flac"),
                Pair("audio/opus", "voice.opus"),
                Pair("audio/x-ms-wma", "audio.wma"),
                Pair("video/x-msvideo", "movie.avi"),
                Pair("video/x-flv", "video.flv"),
                Pair("video/3gpp", "clip.3gp"),
                Pair("video/mp2t", "segment.ts")
            )

            for ((mime, name) in nonRasterFormats) {
                val dummyBytes = ByteArray(32) { (it + 1).toByte() }
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", mime)
                        .setHeader("Content-Length", dummyBytes.size.toString())
                        .setBody(Buffer().write(dummyBytes))
                )
                val url = server.url("/$name").toString()
                val result = OkHttpDownloader.downloadMediaFileDetailed(context, url, name)
                assertTrue("Format $name should succeed without BitmapFactory failure", result.isSuccessful)
                assertNotNull(result.file)
                assertEquals(32L, result.file?.length())
            }
        } finally {
            server.shutdown()
        }
    }
}
