package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaDeduplicator
import com.amresalehin.emregallery.data.MediaProvenance
import com.amresalehin.emregallery.data.OriginalMediaResolver
import com.amresalehin.emregallery.data.ScrapedMediaItem
import com.amresalehin.emregallery.data.SrcsetParser
import com.amresalehin.emregallery.data.WebUrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaResolutionAndDeduplicationTest {

    @Test
    fun mediaClassifierCanonicalDeduplicationStripsTrackingParams() {
        val urlWithTracking = "https://example.com/image.jpg?utm_source=twitter&utm_medium=social&id=42&fbclid=xyz"
        val canonical = MediaDeduplicator.canonicalKey(urlWithTracking)
        assertTrue(canonical.contains("id=42"))
        assertFalse(canonical.contains("utm_source"))
        assertFalse(canonical.contains("fbclid"))

        val urlDifferentTracking = "https://example.com/image.jpg?fbclid=different&id=42&utm_campaign=winter"
        val canonical2 = MediaDeduplicator.canonicalKey(urlDifferentTracking)
        assertEquals(canonical, canonical2)
    }

    @Test
    fun mediaClassifierUrlResolution() {
        val base = "https://example.com/articles/index.html"
        val relative = WebUrlUtils.resolveUrl("/images/hero.png", base)
        assertEquals("https://example.com/images/hero.png", relative)

        val protocolRelative = WebUrlUtils.resolveUrl("//cdn.example.com/video.mp4", base)
        assertEquals("https://cdn.example.com/video.mp4", protocolRelative)
    }

    @Test
    fun mediaClassifierSrcsetParsing() {
        val srcset = "thumb.jpg 320w, medium.jpg 640w, large.jpg 1280w"
        val parsed = SrcsetParser.parse(srcset, "https://example.com/")
        assertEquals(3, parsed.size)
        assertEquals("https://example.com/thumb.jpg", parsed[0].first)
        assertEquals("320w", parsed[0].second)
        assertEquals("https://example.com/large.jpg", parsed[2].first)
        assertEquals("1280w", parsed[2].second)
    }

    @Test
    fun testWikimediaThumbnailResolvesToFullResolutionOriginal() {
        val thumbUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Taj_Mahal_%28Edited%29.jpeg/500px-Taj_Mahal_%28Edited%29.jpeg"
        val resolved = OriginalMediaResolver.resolve(thumbUrl)

        assertEquals("https://upload.wikimedia.org/wikipedia/commons/1/1d/Taj_Mahal_%28Edited%29.jpeg", resolved.mediaUrl)
        assertEquals(thumbUrl, resolved.thumbnailUrl)
        assertEquals("Taj_Mahal_(Edited).jpeg", resolved.cleanTitle)

        val protoRelative = "//thumb.wikimedia.org/wikipedia/commons/thumb/b/ba/Taj_Mahal-10_%28cropped%29.jpg/330px-Taj_Mahal-10_%28cropped%29.jpg"
        val resolvedProto = OriginalMediaResolver.resolve(protoRelative)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/b/ba/Taj_Mahal-10_%28cropped%29.jpg", resolvedProto.mediaUrl)
        assertEquals("Taj_Mahal-10_(cropped).jpg", resolvedProto.cleanTitle)
    }

    @Test
    fun testWikimediaTranscodedVideoResolvesToOriginalMasterFileAndDeduplicates() {
        val masterUrl = "https://upload.wikimedia.org/wikipedia/commons/1/1a/Taj_Mahal_-_Mausoleum_der_Liebe_und_Weltwunder_aus_Marmor.webm"
        val transcode480p = "https://upload.wikimedia.org/wikipedia/commons/transcoded/1/1a/Taj_Mahal_-_Mausoleum_der_Liebe_und_Weltwunder_aus_Marmor.webm/Taj_Mahal_-_Mausoleum_der_Liebe_und_Weltwunder_aus_Marmor.webm.480p.vp9.webm"
        val transcode240p = "https://upload.wikimedia.org/wikipedia/commons/transcoded/1/1a/Taj_Mahal_-_Mausoleum_der_Liebe_und_Weltwunder_aus_Marmor.webm/Taj_Mahal_-_Mausoleum_der_Liebe_und_Weltwunder_aus_Marmor.webm.240p.vp9.webm"

        val resolved480 = OriginalMediaResolver.resolve(transcode480p)
        assertEquals(masterUrl, resolved480.mediaUrl)
        assertEquals(transcode480p, resolved480.thumbnailUrl)
        assertEquals("Taj_Mahal_-_Mausoleum_der_Liebe_und_Weltwunder_aus_Marmor.webm", resolved480.cleanTitle)

        val keyMaster = MediaDeduplicator.deduplicationKey(masterUrl)
        val key480 = MediaDeduplicator.deduplicationKey(transcode480p)
        val key240 = MediaDeduplicator.deduplicationKey(transcode240p)
        assertEquals(keyMaster, key480)
        assertEquals(keyMaster, key240)

        val domItem = ScrapedMediaItem(
            id = "dom_1",
            url = transcode240p,
            title = "Taj_Mahal_-_Mausoleum_der_Liebe",
            isVideo = true,
            resolutionHint = "250x141",
            thumbnailUrl = transcode240p,
            provenance = MediaProvenance(extractorEngine = "Static HTML (DOM Scrape)")
        )
        val netItem = ScrapedMediaItem(
            id = "net_1",
            url = transcode480p,
            title = "Taj_Mahal_-_Mausoleum_der_Liebe",
            isVideo = true,
            resolutionHint = "Network",
            thumbnailUrl = transcode480p,
            provenance = MediaProvenance(extractorEngine = "Browser Chromium (Network Intercept)")
        )

        val deduplicated = MediaDeduplicator.deduplicateScrapedItems(listOf(domItem, netItem))
        assertEquals(1, deduplicated.size)
        val singleItem = deduplicated[0]
        assertEquals(masterUrl, singleItem.url)
        assertEquals("250x141", singleItem.resolutionHint)
        assertTrue(singleItem.provenance.extractorEngine.contains("DOM Scrape"))
        assertTrue(singleItem.provenance.extractorEngine.contains("Network Intercept"))
    }

    @Test
    fun testMediaWikiThumbResolvesWithCustomUploadPath() {
        val customWikiUrl = "https://mywiki.example.org/custom_folder/thumb/9/91/Diagram.png/400px-Diagram.png"
        val resolved = OriginalMediaResolver.resolve(customWikiUrl)
        assertEquals("https://mywiki.example.org/custom_folder/9/91/Diagram.png", resolved.mediaUrl)
        assertEquals(customWikiUrl, resolved.thumbnailUrl)
        assertEquals("Diagram.png", resolved.cleanTitle)

        val wikimediaUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/12/Taj_Mahal.jpg/500px-Taj_Mahal.jpg"
        val resolvedWm = OriginalMediaResolver.resolve(wikimediaUrl)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/1/12/Taj_Mahal.jpg", resolvedWm.mediaUrl)
        assertEquals(wikimediaUrl, resolvedWm.thumbnailUrl)
        assertEquals("Taj_Mahal.jpg", resolvedWm.cleanTitle)
    }

    @Test
    fun testVideoStreamingSliceCleaningAndCanonicalDeduplication() {
        val slice1 = "https://rr1---sn.googlevideo.com/videoplayback?expire=123&id=abc&itag=22&range=0-1048575&rn=1&sq=1"
        val slice2 = "https://rr1---sn.googlevideo.com/videoplayback?expire=123&id=abc&itag=22&range=1048576-2097151&rn=2&sq=2"

        val key1 = MediaDeduplicator.canonicalKey(slice1)
        val key2 = MediaDeduplicator.canonicalKey(slice2)
        assertEquals(key1, key2)

        val cleaned = WebUrlUtils.cleanMediaUrl(slice1)
        assertFalse(cleaned.contains("range="))
        assertFalse(cleaned.contains("rn="))
        assertFalse(cleaned.contains("sq="))
        assertTrue(cleaned.contains("id=abc"))
        assertTrue(cleaned.contains("itag=22"))
    }

    @Test
    fun deduplicateScrapedItemsAcrossMultipleScrapers() {
        val itemFromStaticHtml = ScrapedMediaItem(
            id = "static_1",
            url = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/10/Taj_Mahal-10_%28cropped%29.jpg/500px-Taj_Mahal-10_%28cropped%29.jpg",
            title = "Taj Mahal Thumbnail",
            isVideo = false,
            thumbnailUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/10/Taj_Mahal-10_%28cropped%29.jpg/500px-Taj_Mahal-10_%28cropped%29.jpg",
            provenance = MediaProvenance(
                extractorEngine = "Static HTML",
                pageUrl = "https://en.wikipedia.org/wiki/Taj_Mahal"
            ),
            downloadHeaders = mapOf("Referer" to "https://en.wikipedia.org/wiki/Taj_Mahal")
        )

        val itemFromBrowser = ScrapedMediaItem(
            id = "browser_1",
            url = "https://upload.wikimedia.org/wikipedia/commons/1/10/Taj_Mahal-10_(cropped).jpg",
            title = "Taj Mahal Original",
            isVideo = false,
            resolutionHint = "2048x1536",
            provenance = MediaProvenance(
                extractorEngine = "Browser",
                pageUrl = "https://en.wikipedia.org/wiki/Taj_Mahal"
            ),
            downloadHeaders = mapOf("User-Agent" to "Mozilla/5.0")
        )

        val distinctItem = ScrapedMediaItem(
            id = "static_2",
            url = "https://upload.wikimedia.org/wikipedia/commons/2/22/Red_Fort.jpg",
            title = "Red Fort",
            isVideo = false,
            provenance = MediaProvenance(
                extractorEngine = "Static HTML",
                pageUrl = "https://en.wikipedia.org/wiki/Taj_Mahal"
            )
        )

        val deduplicated = MediaDeduplicator.deduplicateScrapedItems(listOf(itemFromStaticHtml, itemFromBrowser, distinctItem))
        assertEquals(2, deduplicated.size)

        val mergedTaj = deduplicated.first { it.url.contains("Taj_Mahal-10") }
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/1/10/Taj_Mahal-10_(cropped).jpg", mergedTaj.url)
        assertNotNull(mergedTaj.thumbnailUrl)
        assertTrue(mergedTaj.provenance.extractorEngine.contains("Static HTML"))
        assertTrue(mergedTaj.provenance.extractorEngine.contains("Browser"))
        assertTrue(mergedTaj.downloadHeaders.containsKey("Referer"))
        assertTrue(mergedTaj.downloadHeaders.containsKey("User-Agent"))
    }

    @Test
    fun testMultiHostCdnNormalizationAndDeduplication() {
        val redditPreview = "https://preview.redd.it/abcdef123.jpg?width=640&crop=smart&auto=webp&s=xyz789"
        val redditResolved = OriginalMediaResolver.resolve(redditPreview)
        assertEquals("https://i.redd.it/abcdef123.jpg", redditResolved.mediaUrl)
        assertEquals(redditPreview, redditResolved.thumbnailUrl)

        val redditKey1 = MediaDeduplicator.deduplicationKey(redditPreview)
        val redditKey2 = MediaDeduplicator.deduplicationKey("https://i.redd.it/abcdef123.jpg")
        assertEquals(redditKey1, redditKey2)

        val imgurThumb = "https://i.imgur.com/vAbCdEm.jpg"
        val imgurResolved = OriginalMediaResolver.resolve(imgurThumb)
        assertEquals("https://i.imgur.com/vAbCdE.jpg", imgurResolved.mediaUrl)

        val imgurKey1 = MediaDeduplicator.deduplicationKey(imgurThumb)
        val imgurKey2 = MediaDeduplicator.deduplicationKey("https://i.imgur.com/vAbCdE.jpg")
        assertEquals(imgurKey1, imgurKey2)

        val twitterThumb = "https://pbs.twimg.com/media/F123456?format=jpg&name=small"
        val twitterResolved = OriginalMediaResolver.resolve(twitterThumb)
        assertEquals("https://pbs.twimg.com/media/F123456?format=jpg&name=orig", twitterResolved.mediaUrl)

        val twitterKey1 = MediaDeduplicator.deduplicationKey(twitterThumb)
        val twitterKey2 = MediaDeduplicator.deduplicationKey("https://pbs.twimg.com/media/F123456?format=jpg&name=orig")
        assertEquals(twitterKey1, twitterKey2)

        val wpThumb = "https://i0.wp.com/example.com/wp-content/uploads/2026/04/photo.jpg?resize=300%2C200"
        val wpResolved = OriginalMediaResolver.resolve(wpThumb)
        assertEquals("https://example.com/wp-content/uploads/2026/04/photo.jpg", wpResolved.mediaUrl)

        val wpKey1 = MediaDeduplicator.deduplicationKey(wpThumb)
        val wpKey2 = MediaDeduplicator.deduplicationKey("https://example.com/wp-content/uploads/2026/04/photo.jpg")
        assertEquals(wpKey1, wpKey2)
    }

    @Test
    fun testPinterestResolutionAndFormatVariantDeduplication() {
        val hash = "f732ca2684db4685ff263b65cb466ff4"
        val p1 = "f7"
        val p2 = "32"
        val p3 = "ca"

        val url757 = "https://i.pinimg.com/757x945/$p1/$p2/$p3/$hash.jpg"
        val url736 = "https://i.pinimg.com/736x/$p1/$p2/$p3/$hash.jpeg"
        val url474 = "https://i.pinimg.com/474x/$p1/$p2/$p3/$hash.webp"
        val url236 = "https://i.pinimg.com/236x/$p1/$p2/$p3/$hash.jpg"
        val urlOrig = "https://i.pinimg.com/originals/$p1/$p2/$p3/$hash.jpg"

        val expectedKey = "pinimg:${hash.lowercase()}"
        assertEquals(expectedKey, MediaDeduplicator.deduplicationKey(url757))
        assertEquals(expectedKey, MediaDeduplicator.deduplicationKey(url736))
        assertEquals(expectedKey, MediaDeduplicator.deduplicationKey(url474))
        assertEquals(expectedKey, MediaDeduplicator.deduplicationKey(url236))
        assertEquals(expectedKey, MediaDeduplicator.deduplicationKey(urlOrig))

        val res736 = OriginalMediaResolver.resolve(url736)
        assertEquals("https://i.pinimg.com/originals/$p1/$p2/$p3/$hash.jpg", res736.mediaUrl)
        assertEquals(url736, res736.thumbnailUrl)
    }

    @Test
    fun testResponsiveRasterFormatVariantsDeduplication() {
        val base = "https://example.com/wp-content/uploads/2024/05/hero"
        val wpJpg = "$base-736x918.jpg"
        val wpWebp = "$base-757x945.webp"
        val wpOrig = "$base.jpeg"

        val key1 = MediaDeduplicator.deduplicationKey(wpJpg)
        val key2 = MediaDeduplicator.deduplicationKey(wpWebp)
        val key3 = MediaDeduplicator.deduplicationKey(wpOrig)

        assertEquals(key3, key1)
        assertEquals(key3, key2)

        val cdnThumb = "https://images.example.com/236x/products/shoe.webp?w=236&format=webp"
        val cdnLarge = "https://images.example.com/736x/products/shoe.jpg?w=736&format=jpg"
        assertEquals(
            MediaDeduplicator.deduplicationKey(cdnLarge),
            MediaDeduplicator.deduplicationKey(cdnThumb)
        )
    }

    @Test
    fun testDeduplicateScrapedItemsCollapsesResolutionAndFormatVariants() {
        val hash = "2d4c7a99e43de785bde6214ec042e947"
        val item1 = ScrapedMediaItem(
            id = "item_1",
            url = "https://i.pinimg.com/736x/2d/4c/7a/$hash.jpeg",
            title = "Aishwarya Rai Portrait",
            isVideo = false,
            resolutionHint = "736x1227",
            thumbnailUrl = "https://i.pinimg.com/736x/2d/4c/7a/$hash.jpeg",
            provenance = MediaProvenance(extractorEngine = "Static HTML (DOM Scrape)")
        )
        val item2 = ScrapedMediaItem(
            id = "item_2",
            url = "https://i.pinimg.com/236x/2d/4c/7a/$hash.jpeg",
            title = "Aishwarya Rai Portrait",
            isVideo = false,
            resolutionHint = "236x393",
            thumbnailUrl = "https://i.pinimg.com/236x/2d/4c/7a/$hash.jpeg",
            provenance = MediaProvenance(extractorEngine = "Browser Chromium (Network Intercept)")
        )
        val item3 = ScrapedMediaItem(
            id = "item_3",
            url = "https://i.pinimg.com/474x/2d/4c/7a/$hash.webp",
            title = "Aishwarya Rai Portrait",
            isVideo = false,
            resolutionHint = "474x790",
            thumbnailUrl = "https://i.pinimg.com/474x/2d/4c/7a/$hash.webp",
            provenance = MediaProvenance(extractorEngine = "DOM Scrape")
        )

        val deduplicated = MediaDeduplicator.deduplicateScrapedItems(listOf(item1, item2, item3))
        assertEquals(1, deduplicated.size)

        val winner = deduplicated[0]
        assertEquals("https://i.pinimg.com/originals/2d/4c/7a/$hash.jpg", winner.url)
        assertEquals("736x1227", winner.resolutionHint)
        assertTrue(winner.provenance.extractorEngine.contains("DOM Scrape"))
        assertTrue(winner.provenance.extractorEngine.contains("Network Intercept"))
        assertNotNull(winner.thumbnailUrl)
    }
}
