package com.amresalehin.emregallery.data

class GalleryDlProvider : ExtractionProvider {
    override fun isApplicable(context: MediaRequestContext): Boolean {
        return GalleryDlEngine.isAvailable() && GalleryDlEngine.checkSupport(context.url).isSupported
    }

    override suspend fun extract(
        context: MediaRequestContext,
        onItemDiscovered: (ScrapedMediaItem) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): MediaExtractionResult {
        onStatusUpdate("Extracting with gallery-dl...")
        val cookies = OkHttpDownloader.sharedCookieJar.getCookieString(context.url).takeIf { it.isNotBlank() }
        val res = GalleryDlEngine.extractGalleryDl(
            context.url,
            GalleryDlOptions(
                userAgent = OkHttpDownloader.DEFAULT_USER_AGENT,
                cookies = cookies
            )
        )
        if (res.success) {
            for (item in res.items) {
                onItemDiscovered(item)
            }
            return MediaExtractionResult(items = res.items, pageTitle = res.pageTitle)
        }
        return MediaExtractionResult(items = emptyList(), errorMessage = res.errorMessage)
    }
}

class YtDlpProvider : ExtractionProvider {
    private val staticImageExts = setOf(
        ".jpg", ".jpeg", ".png", ".webp", ".gif", ".svg", ".avif", ".bmp", ".ico"
    )

    override fun isApplicable(context: MediaRequestContext): Boolean {
        if (!GalleryDlEngine.isAvailable()) return false
        val lower = context.url.lowercase()
        if (lower.startsWith("data:") || lower.startsWith("blob:")) return false
        val cleanPath = lower.substringBefore('?').substringBefore('#')
        if (staticImageExts.any { cleanPath.endsWith(it) }) return false
        return GalleryDlEngine.checkYtDlpSupport(context.url)
    }

    override suspend fun extract(
        context: MediaRequestContext,
        onItemDiscovered: (ScrapedMediaItem) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): MediaExtractionResult {
        onStatusUpdate("Extracting with yt-dlp...")
        val cookies = OkHttpDownloader.sharedCookieJar.getCookieString(context.url).takeIf { it.isNotBlank() }
        val res = GalleryDlEngine.extractYtDlp(
            context.url,
            GalleryDlOptions(
                userAgent = OkHttpDownloader.DEFAULT_USER_AGENT,
                cookies = cookies
            )
        )
        if (res.success) {
            for (item in res.items) {
                onItemDiscovered(item)
            }
            return MediaExtractionResult(items = res.items, pageTitle = res.pageTitle)
        }
        return MediaExtractionResult(items = emptyList(), errorMessage = res.errorMessage)
    }
}
