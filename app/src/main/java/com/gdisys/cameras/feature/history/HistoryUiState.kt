package com.gdisys.cameras.feature.history

import androidx.annotation.StringRes
import com.gdisys.cameras.R
import java.time.LocalDate
import java.time.YearMonth

/** @property camera the camera (`cam_xx`) whose recordings are listed */
data class HistoryUiState(
  val camera: String,
  val content: HistoryContent = HistoryContent.Loading
)

sealed interface HistoryContent {
  /** Waiting for the tunnel, then for the list of months. */
  data object Loading : HistoryContent

  data class Failed(val error: HistoryError) : HistoryContent

  /** The camera has no folder with recordings on the share. */
  data object Empty : HistoryContent

  /** Newest first. */
  data class Months(val months: List<MonthItem>) : HistoryContent
}

/** A month of the list; [days] says whether it is expanded and what it holds. */
data class MonthItem(
  val month: YearMonth,
  val days: DaysState = DaysState.Collapsed
)

sealed interface DaysState {
  data object Collapsed : DaysState

  data object Loading : DaysState

  /** In calendar order. Empty when the month's folders hold no finished file. */
  data class Loaded(val days: List<LocalDate>) : DaysState

  data class Failed(val error: HistoryError) : DaysState
}

/** What the screen tells the user when something could not be read. */
enum class HistoryError(@get:StringRes val resId: Int) {
  VPN_CONNECTION(R.string.history_error_vpn_connection),
  NOT_ADMIN(R.string.history_error_not_admin),
  AUTHENTICATION_FAILED(R.string.history_error_authentication_failed),
  UNREACHABLE(R.string.history_error_unreachable),
  FAILED(R.string.history_error_failed)
}
