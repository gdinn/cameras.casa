package com.gdisys.cameras.feature.history.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import com.gdisys.cameras.feature.history.logic.RecordingChoice
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The recording picked for a time, with the notice when it does not start at that time. A stand-in
 * until the player exists: then the choice opens the player, which shows the notice.
 */
@Composable
fun RecordingChoiceBar(
  choice: RecordingChoice,
  locale: Locale,
  onDismiss: () -> Unit
) {
  val time = remember(choice, locale) {
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(choice.recording.start)
  }
  val date = remember(choice, locale) {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(choice.recording.start)
  }
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    modifier = Modifier
      .fillMaxWidth()
      .testTag(HistoryTestTags.CHOICE)
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.history_choice_title, date, time),
          style = MaterialTheme.typography.titleSmall
        )
        if (!choice.startsAtRequestedSlot) {
          Text(
            text = stringResource(R.string.history_choice_nearest, time),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(HistoryTestTags.CHOICE_NEAREST_NOTICE)
          )
        }
      }
      TextButton(onClick = onDismiss, modifier = Modifier.testTag(HistoryTestTags.CHOICE_DISMISS_BUTTON)) {
        Text(stringResource(R.string.history_choice_dismiss))
      }
    }
  }
}
