package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.DownloadFilenameFormatter
import com.amresalehin.emregallery.data.MediaFormatOption
import com.amresalehin.emregallery.data.MediaFormatSelectionHelper
import com.amresalehin.emregallery.data.ScrapedMediaItem
import com.amresalehin.emregallery.data.preferences.DownloaderPreferences

/**
 * Dialog allowing users to choose exact video quality, container/codec, or Audio-Only
 * for an individual discovered video, stream, or audio item before downloading.
 */
@Composable
fun MediaFormatPickerDialog(
    item: ScrapedMediaItem,
    selectedOption: MediaFormatOption?,
    onSelectOption: (MediaFormatOption?) -> Unit,
    onDismiss: () -> Unit
) {
    val options = remember(item) { MediaFormatSelectionHelper.parseOptions(item) }
    val durationText = remember(item) {
        DownloadFilenameFormatter.formatDurationSeconds(
            item.durationSeconds ?: item.metadata["duration"]?.toLongOrNull()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Select Quality & Format",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = buildString {
                        append(item.title)
                        if (durationText != null) {
                            append(" • ")
                            append(durationText)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .testTag("format_picker_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    val isDefaultSelected = selectedOption == null
                    FormatOptionRow(
                        label = "Auto (Best Available)",
                        subtitle = "Follows global quality preference",
                        isSelected = isDefaultSelected,
                        isAudioOnly = false,
                        onClick = { onSelectOption(null) },
                        modifier = Modifier.testTag("format_option_auto")
                    )
                }

                items(options, key = { it.formatId + "_" + it.ext }) { option ->
                    val isSelected = selectedOption?.formatId == option.formatId
                    val sub = buildList {
                        if (option.width != null && option.height != null) add("${option.width}×${option.height}")
                        option.vcodec?.takeIf { it != "none" }?.let { add("Video: $it") }
                        option.acodec?.takeIf { it != "none" }?.let { add("Audio: $it") }
                    }.joinToString(" • ").ifBlank {
                        if (option.isAudioOnly) "Audio stream (${option.ext.uppercase()})"
                        else "${option.ext.uppercase()} stream"
                    }

                    FormatOptionRow(
                        label = option.label,
                        subtitle = sub,
                        isSelected = isSelected,
                        isAudioOnly = option.isAudioOnly,
                        onClick = { onSelectOption(option) },
                        modifier = Modifier.testTag("format_option_${option.formatId}")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun FormatOptionRow(
    label: String,
    subtitle: String,
    isSelected: Boolean,
    isAudioOnly: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isAudioOnly) Icons.Default.Audiotrack else Icons.Default.Videocam,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Dialog configuring downloader filename templates (%(title)s, %(uploader)s, %(upload_date)s, %(id)s),
 * per-domain subfolder organization, and default video/audio quality preset.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloaderSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    DownloaderPreferences.ensureInitialized(context)

    val savedTemplate by DownloaderPreferences.filenameTemplate.collectAsState()
    val savedSubfolder by DownloaderPreferences.subfolderByDomain.collectAsState()
    val savedQuality by DownloaderPreferences.preferredQualityPreset.collectAsState()

    var templateDraft by remember(savedTemplate) { mutableStateOf(savedTemplate) }
    var subfolderDraft by remember(savedSubfolder) { mutableStateOf(savedSubfolder) }
    var qualityDraft by remember(savedQuality) { mutableStateOf(savedQuality) }

    val previewName = remember(templateDraft, qualityDraft) {
        val ext = if (qualityDraft == MediaFormatSelectionHelper.PRESET_AUDIO_ONLY) "m4a" else "mp4"
        DownloadFilenameFormatter.formatFileName(
            template = templateDraft,
            title = "Sample Highlight",
            ext = ext,
            uploader = "Creator",
            uploadDate = "20260401",
            mediaId = "abc123",
            domain = "example.com"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Download Options",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Default Video Quality",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MediaFormatSelectionHelper.QUALITY_PRESETS.forEach { (key, label) ->
                        FilterChip(
                            selected = qualityDraft == key,
                            onClick = { qualityDraft = key },
                            label = { Text(label) },
                            modifier = Modifier.testTag("quality_preset_chip_$key")
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = "Filename Template",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = templateDraft,
                    onValueChange = { templateDraft = it },
                    singleLine = true,
                    label = { Text("Template") },
                    placeholder = { Text("%(title)s") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("downloader_filename_template_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DownloadFilenameFormatter.TEMPLATE_PRESETS.forEach { (label, tpl) ->
                        FilterChip(
                            selected = templateDraft.trim() == tpl,
                            onClick = { templateDraft = tpl },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DownloadFilenameFormatter.SUPPORTED_TOKENS.forEach { token ->
                        FilterChip(
                            selected = templateDraft.contains(token),
                            onClick = {
                                templateDraft = if (templateDraft.isBlank()) {
                                    token
                                } else if (templateDraft.endsWith(" ") || templateDraft.endsWith("-") || templateDraft.endsWith("_")) {
                                    "$templateDraft$token"
                                } else {
                                    "$templateDraft - $token"
                                }
                            },
                            label = { Text(token, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Preview: ${if (subfolderDraft) "example.com/" else ""}$previewName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Subfolder by domain",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Save inside Emre Gallery/<domain>/",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = subfolderDraft,
                        onCheckedChange = { subfolderDraft = it },
                        modifier = Modifier.testTag("downloader_subfolder_domain_switch")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    DownloaderPreferences.setFilenameTemplate(context, templateDraft)
                    DownloaderPreferences.setSubfolderByDomain(context, subfolderDraft)
                    DownloaderPreferences.setPreferredQualityPreset(context, qualityDraft)
                    onDismiss()
                },
                modifier = Modifier.testTag("downloader_settings_save_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
