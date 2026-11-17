package com.amresalehin.emregallery.data.preferences

import android.content.Context
import com.amresalehin.emregallery.data.DownloadFilenameFormatter
import com.amresalehin.emregallery.data.MediaFormatSelectionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Synchronous + reactive SharedPreferences-backed store for Media Downloader preferences:
 * - filenameTemplate (supports %(title)s, %(uploader)s, %(upload_date)s, %(id)s)
 * - subfolderByDomain (organizes downloads under Emre Gallery/<domain>/)
 * - preferredQualityPreset (best, 1080p, 720p, 480p, audio_only)
 */
object DownloaderPreferences {

    private const val PREFS_NAME = "emre_gallery_downloader_prefs"
    private const val KEY_FILENAME_TEMPLATE = "filename_template"
    private const val KEY_SUBFOLDER_BY_DOMAIN = "subfolder_by_domain"
    private const val KEY_PREFERRED_QUALITY = "preferred_quality_preset"

    private val _filenameTemplate = MutableStateFlow(DownloadFilenameFormatter.DEFAULT_FILENAME_TEMPLATE)
    val filenameTemplate: StateFlow<String> = _filenameTemplate.asStateFlow()

    private val _subfolderByDomain = MutableStateFlow(false)
    val subfolderByDomain: StateFlow<Boolean> = _subfolderByDomain.asStateFlow()

    private val _preferredQualityPreset = MutableStateFlow(MediaFormatSelectionHelper.PRESET_BEST)
    val preferredQualityPreset: StateFlow<String> = _preferredQualityPreset.asStateFlow()

    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _filenameTemplate.value = prefs.getString(
                KEY_FILENAME_TEMPLATE,
                DownloadFilenameFormatter.DEFAULT_FILENAME_TEMPLATE
            )?.takeIf { it.isNotBlank() } ?: DownloadFilenameFormatter.DEFAULT_FILENAME_TEMPLATE
            _subfolderByDomain.value = prefs.getBoolean(KEY_SUBFOLDER_BY_DOMAIN, false)
            _preferredQualityPreset.value = prefs.getString(
                KEY_PREFERRED_QUALITY,
                MediaFormatSelectionHelper.PRESET_BEST
            ) ?: MediaFormatSelectionHelper.PRESET_BEST
            initialized = true
        }
    }

    fun getFilenameTemplate(context: Context): String {
        ensureInitialized(context)
        return _filenameTemplate.value
    }

    fun isSubfolderByDomainEnabled(context: Context): Boolean {
        ensureInitialized(context)
        return _subfolderByDomain.value
    }

    fun getPreferredQualityPreset(context: Context): String {
        ensureInitialized(context)
        return _preferredQualityPreset.value
    }

    fun setFilenameTemplate(context: Context, template: String) {
        ensureInitialized(context)
        val normalized = template.trim().ifBlank { DownloadFilenameFormatter.DEFAULT_FILENAME_TEMPLATE }
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FILENAME_TEMPLATE, normalized)
            .apply()
        _filenameTemplate.value = normalized
    }

    fun setSubfolderByDomain(context: Context, enabled: Boolean) {
        ensureInitialized(context)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SUBFOLDER_BY_DOMAIN, enabled)
            .apply()
        _subfolderByDomain.value = enabled
    }

    fun setPreferredQualityPreset(context: Context, preset: String) {
        ensureInitialized(context)
        val normalized = preset.trim().ifBlank { MediaFormatSelectionHelper.PRESET_BEST }
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREFERRED_QUALITY, normalized)
            .apply()
        _preferredQualityPreset.value = normalized
    }
}
