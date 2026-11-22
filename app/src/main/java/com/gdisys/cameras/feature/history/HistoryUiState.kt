package com.gdisys.cameras.feature.history

import androidx.annotation.StringRes
import com.gdisys.cameras.R
import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.feature.history.logic.RecordingChoice
import java.time.LocalDate
import java.time.YearMonth

/**
 * @property camera the camera (`cam_xx`) whose recordings are listed
 * @property picker the time picker of the day the user tapped, over the list
 */
data class HistoryUiState(
  val camera: String,
  val content: HistoryContent = HistoryContent.Loading,
  val picker: DayPicker? = null
)

/** The time picker of [day]: its recordings are listed when it opens. */
sealed interface DayPicker {
  val day: LocalDate

  data class Loading(override val day: LocalDate) : DayPicker

  /** [recordings] in chronological order; empty when the day's files are gone since the listing. */
  data class Ready(override val day: LocalDate, val recordings: List<Recording>) : DayPicker

  data class Failed(override val day: LocalDate, val error: HistoryError) : DayPicker
}

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
  NO_SAMBA_LOGIN(R.string.history_error_no_samba_login),
  AUTHENTICATION_FAILED(R.string.history_error_authentication_failed),
  UNREACHABLE(R.string.history_error_unreachable),
  FAILED(R.string.history_error_failed)
}

/** The message for a failure of the share; anything else is [HistoryError.FAILED]. */
internal fun Throwable.toHistoryError(): HistoryError = when (this) {
  is RecordingsException.NoSambaLogin -> HistoryError.NO_SAMBA_LOGIN
  is RecordingsException.AuthenticationFailed -> HistoryError.AUTHENTICATION_FAILED
  is RecordingsException.Unreachable -> HistoryError.UNREACHABLE
  else -> HistoryError.FAILED
}

sealed interface HistoryNavigateUiEvent {
  /** Plays the recording picked for a time. */
  data class ToPlayer(val choice: RecordingChoice) : HistoryNavigateUiEvent
}
