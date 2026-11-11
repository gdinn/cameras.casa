package com.gdisys.cameras.feature.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import com.gdisys.cameras.core.components.LoadingScreen
import com.gdisys.cameras.feature.history.DaysState
import com.gdisys.cameras.feature.history.HistoryContent
import com.gdisys.cameras.feature.history.HistoryError
import com.gdisys.cameras.feature.history.HistoryUiState
import com.gdisys.cameras.feature.history.MonthItem
import com.gdisys.cameras.ui.theme.CamerasTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
  uiState: HistoryUiState,
  onBack: () -> Unit,
  onRetry: () -> Unit,
  onMonthClicked: (YearMonth) -> Unit,
  onRetryMonth: (YearMonth) -> Unit,
  onDayClicked: (LocalDate) -> Unit,
  onRetryDay: () -> Unit,
  onPickerDismissed: () -> Unit,
  onSlotSelected: (LocalTime) -> Unit,
  onChoiceDismissed: () -> Unit
) {
  val locale = LocalConfiguration.current.locales[0]

  uiState.picker?.let { picker ->
    TimePickerSheet(
      picker = picker,
      locale = locale,
      onSlotSelected = onSlotSelected,
      onRetry = onRetryDay,
      onDismiss = onPickerDismissed
    )
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    bottomBar = {
      uiState.choice?.let { RecordingChoiceBar(choice = it, locale = locale, onDismiss = onChoiceDismissed) }
    },
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.history_screen_title, uiState.camera)) },
        navigationIcon = {
          IconButton(
            modifier = Modifier.testTag(HistoryTestTags.BACK_BUTTON),
            onClick = onBack
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.history_screen_navigate_back)
            )
          }
        }
      )
    }
  ) { innerPadding ->
    val modifier = Modifier
      .fillMaxSize()
      .padding(innerPadding)
    when (val content = uiState.content) {
      HistoryContent.Loading -> LoadingScreen(
        textLabel = stringResource(R.string.history_screen_loading),
        modifier = modifier.testTag(HistoryTestTags.LOADING)
      )

      is HistoryContent.Failed -> CenteredMessage(
        text = stringResource(content.error.resId),
        modifier = modifier.testTag(HistoryTestTags.ERROR)
      ) {
        Button(onClick = onRetry, modifier = Modifier.testTag(HistoryTestTags.RETRY_BUTTON)) {
          Text(stringResource(R.string.history_screen_retry))
        }
      }

      HistoryContent.Empty -> CenteredMessage(
        text = stringResource(R.string.history_screen_empty),
        modifier = modifier.testTag(HistoryTestTags.EMPTY)
      )

      is HistoryContent.Months -> MonthList(
        months = content.months,
        locale = locale,
        onMonthClicked = onMonthClicked,
        onRetryMonth = onRetryMonth,
        onDayClicked = onDayClicked,
        modifier = modifier.testTag(HistoryTestTags.MONTH_LIST)
      )
    }
  }
}

@Composable
private fun CenteredMessage(
  text: String,
  modifier: Modifier = Modifier,
  action: @Composable () -> Unit = {}
) {
  Column(
    modifier = modifier.padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(text = text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    action()
  }
}

@Composable
private fun MonthList(
  months: List<MonthItem>,
  locale: Locale,
  onMonthClicked: (YearMonth) -> Unit,
  onRetryMonth: (YearMonth) -> Unit,
  onDayClicked: (LocalDate) -> Unit,
  modifier: Modifier = Modifier
) {
  LazyColumn(modifier = modifier) {
    items(months, key = { it.month.toString() }) { item ->
      MonthHeader(item = item, locale = locale, onClick = { onMonthClicked(item.month) })
      MonthDays(item = item, locale = locale, onRetry = { onRetryMonth(item.month) }, onDayClicked = onDayClicked)
      HorizontalDivider()
    }
  }
}

@Composable
private fun MonthHeader(item: MonthItem, locale: Locale, onClick: () -> Unit) {
  val expanded = item.days != DaysState.Collapsed
  val stateText = stringResource(
    if (expanded) R.string.history_screen_month_expanded else R.string.history_screen_month_collapsed
  )
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(role = Role.Button, onClick = onClick)
      .semantics { stateDescription = stateText }
      .testTag(HistoryTestTags.month(item.month))
      .padding(horizontal = 16.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = remember(item.month, locale) { monthLabel(item.month, locale) },
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.weight(1f)
    )
    Icon(
      imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
      contentDescription = null
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthDays(item: MonthItem, locale: Locale, onRetry: () -> Unit, onDayClicked: (LocalDate) -> Unit) {
  val month = item.month
  val padding = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
  when (val days = item.days) {
    DaysState.Collapsed -> Unit

    DaysState.Loading -> Box(
      modifier = padding
        .fillMaxWidth()
        .testTag(HistoryTestTags.monthLoading(month)),
      contentAlignment = Alignment.Center
    ) {
      CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }

    is DaysState.Failed -> Row(
      modifier = padding
        .fillMaxWidth()
        .testTag(HistoryTestTags.monthError(month)),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(
        text = stringResource(days.error.resId),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f)
      )
      OutlinedButton(onClick = onRetry, modifier = Modifier.testTag(HistoryTestTags.monthRetryButton(month))) {
        Text(stringResource(R.string.history_screen_retry))
      }
    }

    is DaysState.Loaded -> if (days.days.isEmpty()) {
      Text(
        text = stringResource(R.string.history_screen_month_no_days),
        style = MaterialTheme.typography.bodyMedium,
        modifier = padding.testTag(HistoryTestTags.monthNoDays(month))
      )
    } else {
      FlowRow(
        modifier = padding.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        days.days.forEach { day -> DayItem(day = day, locale = locale, onClick = { onDayClicked(day) }) }
      }
    }
  }
}

/** A day with recordings, which opens its time picker. */
@Composable
private fun DayItem(day: LocalDate, locale: Locale, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.secondaryContainer,
    modifier = Modifier.testTag(HistoryTestTags.day(day))
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(text = day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
      Text(
        text = remember(day, locale) { DateTimeFormatter.ofPattern("EEE", locale).format(day) },
        style = MaterialTheme.typography.labelSmall
      )
    }
  }
}

/** "November 2026" / "Novembro 2026": the standalone month name, capitalized for every language. */
private fun monthLabel(month: YearMonth, locale: Locale): String =
  DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month).replaceFirstChar { it.titlecase(locale) }

@Preview
@Composable
private fun HistoryScreenPreview() {
  CamerasTheme {
    HistoryScreen(
      uiState = HistoryUiState(
        camera = "cam_10",
        content = HistoryContent.Months(
          listOf(
            MonthItem(
              YearMonth.of(2026, 11),
              DaysState.Loaded((1..10).map { LocalDate.of(2026, 11, it) })
            ),
            MonthItem(YearMonth.of(2026, 10), DaysState.Failed(HistoryError.UNREACHABLE)),
            MonthItem(YearMonth.of(2026, 9))
          )
        )
      ),
      onBack = {},
      onRetry = {},
      onMonthClicked = {},
      onRetryMonth = {},
      onDayClicked = {},
      onRetryDay = {},
      onPickerDismissed = {},
      onSlotSelected = {},
      onChoiceDismissed = {}
    )
  }
}
