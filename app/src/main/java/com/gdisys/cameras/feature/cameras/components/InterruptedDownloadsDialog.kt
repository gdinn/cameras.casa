package com.gdisys.cameras.feature.cameras.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import com.gdisys.cameras.core.downloads.domain.model.InterruptedDownload
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The downloads that did not end, with the choice to start them again or drop them. Either way
 * the partial files are deleted. Dismissing sets the question aside until Home opens again.
 */
@Composable
fun InterruptedDownloadsDialog(
  downloads: List<InterruptedDownload>,
  onResume: () -> Unit,
  onDiscard: () -> Unit,
  onDismiss: () -> Unit
) {
  val locale = LocalConfiguration.current.locales[0]
  val formatter = remember(locale) {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag(HomeTestTags.INTERRUPTED_DOWNLOADS_DIALOG),
    title = { Text(stringResource(R.string.home_interrupted_downloads_title)) },
    text = {
      Column(
        modifier = Modifier
          .heightIn(max = 320.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(stringResource(R.string.home_interrupted_downloads_message))
        downloads.forEach { download ->
          val reason = stringResource(
            when (download.reason) {
              InterruptedDownload.Reason.INTERRUPTED -> R.string.home_interrupted_downloads_reason_interrupted
              InterruptedDownload.Reason.FAILED -> R.string.home_interrupted_downloads_reason_failed
            }
          )
          Text(
            text = "${download.recording.camera} · ${formatter.format(download.recording.start)} · $reason",
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onResume, modifier = Modifier.testTag(HomeTestTags.RESUME_DOWNLOADS_BUTTON)) {
        Text(stringResource(R.string.home_interrupted_downloads_resume))
      }
    },
    dismissButton = {
      TextButton(onClick = onDiscard, modifier = Modifier.testTag(HomeTestTags.DISCARD_DOWNLOADS_BUTTON)) {
        Text(stringResource(R.string.home_interrupted_downloads_discard))
      }
    }
  )
}
