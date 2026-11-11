package com.gdisys.cameras.feature.history.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import com.gdisys.cameras.feature.history.DayPicker
import com.gdisys.cameras.feature.history.logic.DAY_SLOTS
import com.gdisys.cameras.feature.history.logic.recordedSlots
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val SLOT_COLUMNS = 4

/**
 * The 96 slots of a day, four per hour, with the slots where a recording starts highlighted. Any
 * slot can be chosen: one without a recording picks the nearest one of the day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSheet(
  picker: DayPicker,
  locale: Locale,
  onSlotSelected: (LocalTime) -> Unit,
  onRetry: () -> Unit,
  onDismiss: () -> Unit
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    modifier = Modifier.testTag(HistoryTestTags.PICKER)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Text(
        text = remember(picker.day, locale) {
          DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(picker.day)
        },
        style = MaterialTheme.typography.titleLarge
      )
      when (picker) {
        is DayPicker.Loading -> Box(
          modifier = Modifier
            .fillMaxWidth()
            .testTag(HistoryTestTags.PICKER_LOADING),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator()
        }

        is DayPicker.Failed -> Column(
          modifier = Modifier
            .fillMaxWidth()
            .testTag(HistoryTestTags.PICKER_ERROR),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(text = stringResource(picker.error.resId), textAlign = TextAlign.Center)
          Button(onClick = onRetry, modifier = Modifier.testTag(HistoryTestTags.PICKER_RETRY_BUTTON)) {
            Text(stringResource(R.string.history_screen_retry))
          }
        }

        is DayPicker.Ready -> if (picker.recordings.isEmpty()) {
          Text(
            text = stringResource(R.string.history_picker_empty),
            modifier = Modifier.testTag(HistoryTestTags.PICKER_EMPTY)
          )
        } else {
          SlotGrid(
            recorded = remember(picker.recordings) { recordedSlots(picker.recordings) },
            locale = locale,
            onSlotSelected = onSlotSelected
          )
        }
      }
    }
  }
}

@Composable
private fun SlotGrid(
  recorded: Set<LocalTime>,
  locale: Locale,
  onSlotSelected: (LocalTime) -> Unit
) {
  val formatter = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
  // Opens on the row of the first recording rather than at midnight.
  val firstRecorded = DAY_SLOTS.indexOfFirst { it in recorded }.coerceAtLeast(0)
  val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = firstRecorded - firstRecorded % SLOT_COLUMNS)
  val recordedDescription = stringResource(R.string.history_picker_slot_recorded)

  LazyVerticalGrid(
    columns = GridCells.Fixed(SLOT_COLUMNS),
    state = gridState,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(max = 480.dp)
      .testTag(HistoryTestTags.SLOT_GRID),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    items(DAY_SLOTS, key = { it.toString() }) { slot ->
      val label = formatter.format(slot)
      val modifier = Modifier.testTag(HistoryTestTags.slot(slot))
      if (slot in recorded) {
        FilledTonalButton(
          onClick = { onSlotSelected(slot) },
          modifier = modifier.semantics { stateDescription = recordedDescription }
        ) {
          Text(label, maxLines = 1)
        }
      } else {
        OutlinedButton(onClick = { onSlotSelected(slot) }, modifier = modifier) {
          Text(label, maxLines = 1)
        }
      }
    }
  }
}
