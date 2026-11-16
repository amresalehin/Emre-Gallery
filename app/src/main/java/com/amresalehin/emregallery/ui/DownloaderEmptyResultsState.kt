package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun DownloaderEmptyResultsState(
    onOpenInLiveBrowser: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null
) {
    val hasError = !errorMessage.isNullOrBlank()
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = if (hasError) MaterialTheme.colorScheme.error else Color(0xFF6C757D),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = if (hasError) "Extraction issue" else "No media discovered",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (hasError) {
                    errorMessage!!
                } else {
                    "The webpage might be dynamically protected, require login, or filter settings are excluding items."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (hasError) MaterialTheme.colorScheme.error else Color(0xFF6C757D),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("downloader_empty_or_error_message")
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onOpenInLiveBrowser,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF212529))
            ) {
                Icon(Icons.Default.TravelExplore, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Open in Live Browser")
            }
        }
    }
}
