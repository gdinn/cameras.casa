package com.gdisys.cameras.feature.history.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gdisys.cameras.R
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.feature.history.DayPicker
import com.gdisys.cameras.feature.history.DaysState
import com.gdisys.cameras.feature.history.HistoryContent
import com.gdisys.cameras.feature.history.HistoryError
import com.gdisys.cameras.feature.history.HistoryUiState
import com.gdisys.cameras.feature.history.MonthItem
import com.gdisys.cameras.testing.hasStateDescription
import com.gdisys.cameras.testing.setThemedContent
import com.gdisys.cameras.testing.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

private val NOV: YearMonth = YearMonth.of(2026, 11)
private val OCT: YearMonth = YearMonth.of(2026, 10)
private val SEP: YearMonth = YearMonth.of(2026, 9)
private val NOV_DAYS = listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 10))
private val DAY: LocalDate = LocalDate.of(2026, 11, 10)
private val AT_1510 = Recording("cam_10", LocalDateTime.of(2026, 11, 10, 15, 10), "cam_10_2026-11-10_15-10-00.mp4")
private val AT_1530 = Recording("cam_10", LocalDateTime.of(2026, 11, 10, 15, 30), "cam_10_2026-11-10_15-30-00.mp4")

/** The History screen with hand-written states and recorded callbacks. */
@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val backs = AtomicInteger()
  private val retries = AtomicInteger()
  private val monthClicks = CopyOnWriteArrayList<YearMonth>()
  private val monthRetries = CopyOnWriteArrayList<YearMonth>()
  private val dayClicks = CopyOnWriteArrayList<LocalDate>()
  private val dayRetries = AtomicInteger()
  private val slots = CopyOnWriteArrayList<LocalTime>()

  private fun setScreen(content: HistoryContent, picker: DayPicker? = null) {
    composeRule.setThemedContent {
      HistoryScreen(
        uiState = HistoryUiState(camera = "cam_10", content = content, picker = picker),
        onBack = { backs.incrementAndGet() },
        onRetry = { retries.incrementAndGet() },
        onMonthClicked = { monthClicks += it },
        onRetryMonth = { monthRetries += it },
        onDayClicked = { dayClicks += it },
        onRetryDay = { dayRetries.incrementAndGet() },
        onPickerDismissed = {},
        onSlotSelected = { slots += it }
      )
    }
  }

  @Test
  fun loadingShowsTheSpinnerAndBackWorks() {
    setScreen(HistoryContent.Loading)

    composeRule.onNodeWithTag(HistoryTestTags.LOADING).assertIsDisplayed()
    composeRule.onNodeWithTag(HistoryTestTags.BACK_BUTTON).performClick()

    assertEquals(1, backs.get())
  }

  @Test
  fun aFailureShowsItsMessageAndRetries() {
    setScreen(HistoryContent.Failed(HistoryError.AUTHENTICATION_FAILED))

    composeRule.onNodeWithText(string(R.string.history_error_authentication_failed)).assertIsDisplayed()
    composeRule.onNodeWithTag(HistoryTestTags.RETRY_BUTTON).performClick()

    assertEquals(1, retries.get())
  }

  @Test
  fun emptyTellsThereIsNothing() {
    setScreen(HistoryContent.Empty)

    composeRule.onNodeWithTag(HistoryTestTags.EMPTY).assertIsDisplayed()
  }

  @Test
  fun monthsShowEachState() {
    setScreen(
      HistoryContent.Months(
        listOf(
          MonthItem(NOV, DaysState.Loaded(NOV_DAYS)),
          MonthItem(OCT, DaysState.Failed(HistoryError.UNREACHABLE)),
          MonthItem(SEP, DaysState.Loading),
          MonthItem(YearMonth.of(2026, 8), DaysState.Loaded(emptyList())),
          MonthItem(YearMonth.of(2026, 7))
        )
      )
    )

    composeRule.onNodeWithTag(HistoryTestTags.month(NOV))
      .assert(hasStateDescription(R.string.history_screen_month_expanded))
    NOV_DAYS.forEach { composeRule.onNodeWithTag(HistoryTestTags.day(it)).assertIsDisplayed() }
    composeRule.onNodeWithTag(HistoryTestTags.monthError(OCT)).assertIsDisplayed()
    composeRule.onNodeWithTag(HistoryTestTags.monthLoading(SEP)).assertIsDisplayed()
    composeRule.onNodeWithTag(HistoryTestTags.monthNoDays(YearMonth.of(2026, 8))).assertIsDisplayed()
    composeRule.onNodeWithTag(HistoryTestTags.month(YearMonth.of(2026, 7)))
      .assert(hasStateDescription(R.string.history_screen_month_collapsed))
  }

  @Test
  fun monthsAndTheirRetryReportClicks() {
    setScreen(
      HistoryContent.Months(
        listOf(MonthItem(NOV), MonthItem(OCT, DaysState.Failed(HistoryError.UNREACHABLE)))
      )
    )

    composeRule.onNodeWithTag(HistoryTestTags.month(NOV)).performClick()
    composeRule.onNodeWithTag(HistoryTestTags.monthRetryButton(OCT)).performClick()

    assertEquals(listOf(NOV), monthClicks)
    assertEquals(listOf(OCT), monthRetries)
  }

  @Test
  fun aDayReportsItsClick() {
    setScreen(HistoryContent.Months(listOf(MonthItem(NOV, DaysState.Loaded(NOV_DAYS)))))

    composeRule.onNodeWithTag(HistoryTestTags.day(DAY)).performClick()

    assertEquals(listOf(DAY), dayClicks)
  }

  @Test
  fun thePickerHighlightsRecordedSlotsAndReportsAnyChosenSlot() {
    setScreen(
      HistoryContent.Months(listOf(MonthItem(NOV))),
      picker = DayPicker.Ready(DAY, listOf(AT_1510, AT_1530))
    )

    composeRule.onNodeWithTag(HistoryTestTags.slot(LocalTime.of(15, 0)))
      .assert(hasStateDescription(R.string.history_picker_slot_recorded))
    composeRule.onNodeWithTag(HistoryTestTags.slot(LocalTime.of(15, 30)))
      .assert(hasStateDescription(R.string.history_picker_slot_recorded))
    composeRule.onNodeWithTag(HistoryTestTags.slot(LocalTime.of(15, 15)))
      .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))

    composeRule.onNodeWithTag(HistoryTestTags.slot(LocalTime.of(15, 15))).performClick()
    composeRule.onNodeWithTag(HistoryTestTags.slot(LocalTime.of(15, 30))).performClick()

    assertEquals(listOf(LocalTime.of(15, 15), LocalTime.of(15, 30)), slots)
  }

  @Test
  fun thePickerShowsItsLoadingState() {
    setScreen(HistoryContent.Months(listOf(MonthItem(NOV))), picker = DayPicker.Loading(DAY))
    composeRule.onNodeWithTag(HistoryTestTags.PICKER_LOADING).assertIsDisplayed()
  }

  @Test
  fun anEmptyDayHasNoSlots() {
    setScreen(HistoryContent.Months(listOf(MonthItem(NOV))), picker = DayPicker.Ready(DAY, emptyList()))

    composeRule.onNodeWithTag(HistoryTestTags.PICKER_EMPTY).assertIsDisplayed()
    composeRule.onNodeWithTag(HistoryTestTags.SLOT_GRID).assertDoesNotExist()
  }

  @Test
  fun aFailedDayRetries() {
    setScreen(
      HistoryContent.Months(listOf(MonthItem(NOV))),
      picker = DayPicker.Failed(DAY, HistoryError.UNREACHABLE)
    )

    composeRule.onNodeWithTag(HistoryTestTags.PICKER_RETRY_BUTTON).performClick()

    assertEquals(1, dayRetries.get())
  }
}
