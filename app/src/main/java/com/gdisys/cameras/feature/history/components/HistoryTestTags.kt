package com.gdisys.cameras.feature.history.components

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** Semantic tags shared by the History screen and its instrumented tests. */
object HistoryTestTags {
  const val BACK_BUTTON = "history_back_button"
  const val LOADING = "history_loading"
  const val ERROR = "history_error"
  const val RETRY_BUTTON = "history_retry_button"
  const val EMPTY = "history_empty"
  const val MONTH_LIST = "history_month_list"
  const val PICKER = "history_picker"
  const val PICKER_LOADING = "history_picker_loading"
  const val PICKER_ERROR = "history_picker_error"
  const val PICKER_RETRY_BUTTON = "history_picker_retry"
  const val PICKER_EMPTY = "history_picker_empty"
  const val SLOT_GRID = "history_slot_grid"

  /** The header of [month], which expands and collapses it. */
  fun month(month: YearMonth) = "history_month:$month"

  fun monthLoading(month: YearMonth) = "history_month_loading:$month"

  fun monthNoDays(month: YearMonth) = "history_month_no_days:$month"

  fun monthError(month: YearMonth) = "history_month_error:$month"

  fun monthRetryButton(month: YearMonth) = "history_month_retry:$month"

  fun day(day: LocalDate) = "history_day:$day"

  /** A slot of the time picker, by its start (`15:00`). */
  fun slot(slot: LocalTime) = "history_slot:$slot"
}
