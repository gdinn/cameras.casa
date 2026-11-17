package com.gdisys.cameras.feature.history

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.ToastEventViewModel
import com.gdisys.cameras.core.recordings.domain.usecase.GetRecordingDaysUseCase
import com.gdisys.cameras.core.recordings.domain.usecase.GetRecordingMonthsUseCase
import com.gdisys.cameras.core.recordings.domain.usecase.GetRecordingsOfDayUseCase
import com.gdisys.cameras.core.vpn.domain.TunnelCoordinator
import com.gdisys.cameras.core.vpn.domain.TunnelHolder
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.gdisys.cameras.core.vpn.domain.usecase.ObserveVpnStateUseCase
import com.gdisys.cameras.feature.history.logic.nearestRecording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject

/**
 * The route argument holding the camera. Type-safe navigation stores a route's properties in the
 * `SavedStateHandle` under their names, so this is the name of `NavigationRoute.History.camera`.
 */
const val HISTORY_CAMERA_ARG = "camera"

/** History's registration with the [TunnelCoordinator]. */
internal val HISTORY_TUNNEL_HOLDER = TunnelHolder.Screen("history")

/**
 * The months with recordings of one camera, each expandable into its days.
 *
 * Like Home, History holds the tunnel while it is resumed. Coming from Home's focused view, the
 * hand-over happens within the coordinator's grace period, so the tunnel stays up. The months are
 * listed once the tunnel is up, and only once: coming back from the background keeps the list. A
 * month's days are listed when it is expanded, every time, since a new file may have finished.
 *
 * Tapping a day opens its time picker, which lists the day's recordings. Choosing a time picks the
 * nearest recording of that day (see [nearestRecording]) and opens the player on it.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  observeVpnStateUseCase: ObserveVpnStateUseCase,
  private val getRecordingMonthsUseCase: GetRecordingMonthsUseCase,
  private val getRecordingDaysUseCase: GetRecordingDaysUseCase,
  private val getRecordingsOfDayUseCase: GetRecordingsOfDayUseCase,
  private val tunnelCoordinator: TunnelCoordinator
) : ToastEventViewModel() {

  private val camera: String = checkNotNull(savedStateHandle[HISTORY_CAMERA_ARG]) {
    "History opened without a camera"
  }

  private val _uiState = MutableStateFlow(HistoryUiState(camera))
  val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

  private val _navigateUiEvent = Channel<HistoryNavigateUiEvent>()
  val navigateUiEvent = _navigateUiEvent.receiveAsFlow()

  private var resumed = false
  private var tunnelUp = false
  private var monthsJob: Job? = null
  private val daysJobs = mutableMapOf<YearMonth, Job>()
  private var pickerJob: Job? = null

  init {
    viewModelScope.launch {
      observeVpnStateUseCase().collect { state ->
        tunnelUp = state == VpnTunnelState.CONNECTED
        loadMonthsIfWaiting()
      }
    }

    viewModelScope.launch {
      tunnelCoordinator.connectFailures.collect { e ->
        if (!resumed || _uiState.value.content != HistoryContent.Loading) return@collect
        Log.d(DEBUG_TAG, e.message.toString())
        setContent(HistoryContent.Failed(HistoryError.VPN_CONNECTION))
      }
    }
  }

  fun onResumed() {
    resumed = true
    tunnelCoordinator.request(HISTORY_TUNNEL_HOLDER)
  }

  fun onPaused() {
    resumed = false
    tunnelCoordinator.release(HISTORY_TUNNEL_HOLDER)
  }

  /** Lists the months again after a failure. Asking for the tunnel again retries a failed connect. */
  fun onRetry() {
    if (_uiState.value.content !is HistoryContent.Failed) return
    setContent(HistoryContent.Loading)
    if (resumed) tunnelCoordinator.request(HISTORY_TUNNEL_HOLDER)
    loadMonthsIfWaiting()
  }

  /** Expands a collapsed month, listing its days, or collapses an expanded one. */
  fun onMonthClicked(month: YearMonth) {
    val item = months()?.firstOrNull { it.month == month } ?: return
    if (item.days == DaysState.Collapsed) loadDays(month) else collapse(month)
  }

  /** Lists a month's days again after they failed. */
  fun onRetryMonth(month: YearMonth) {
    val item = months()?.firstOrNull { it.month == month } ?: return
    if (item.days is DaysState.Failed) loadDays(month)
  }

  /** Opens the time picker of [day] and lists its recordings. */
  fun onDayClicked(day: LocalDate) {
    pickerJob?.cancel()
    _uiState.update { it.copy(picker = DayPicker.Loading(day)) }
    pickerJob = viewModelScope.launch {
      val picker = getRecordingsOfDayUseCase(camera, day).fold(
        onSuccess = { recordings -> DayPicker.Ready(day, recordings) },
        onFailure = { e ->
          Log.d(DEBUG_TAG, "Listing the recordings of $camera $day failed: $e")
          DayPicker.Failed(day, e.toHistoryError())
        }
      )
      _uiState.update { it.copy(picker = picker) }
    }
  }

  /** Lists the day's recordings again after they failed. */
  fun onRetryDay() {
    val picker = _uiState.value.picker as? DayPicker.Failed ?: return
    onDayClicked(picker.day)
  }

  fun onPickerDismissed() {
    pickerJob?.cancel()
    _uiState.update { it.copy(picker = null) }
  }

  /** Picks the recording of the open day nearest to [slot], closes the picker and opens the player. */
  fun onSlotSelected(slot: LocalTime) {
    val picker = _uiState.value.picker as? DayPicker.Ready ?: return
    val choice = nearestRecording(picker.recordings, slot) ?: return
    _uiState.update { it.copy(picker = null) }
    viewModelScope.launch { _navigateUiEvent.send(HistoryNavigateUiEvent.ToPlayer(choice)) }
  }

  override fun onCleared() {
    tunnelCoordinator.release(HISTORY_TUNNEL_HOLDER)
    super.onCleared()
  }

  private fun loadMonthsIfWaiting() {
    if (!tunnelUp || _uiState.value.content != HistoryContent.Loading || monthsJob?.isActive == true) return
    monthsJob = viewModelScope.launch {
      getRecordingMonthsUseCase(camera).fold(
        onSuccess = { months ->
          setContent(
            if (months.isEmpty()) HistoryContent.Empty else HistoryContent.Months(months.map { MonthItem(it) })
          )
        },
        onFailure = { e ->
          Log.d(DEBUG_TAG, "Listing the months of $camera failed: $e")
          setContent(HistoryContent.Failed(e.toHistoryError()))
        }
      )
    }
  }

  private fun loadDays(month: YearMonth) {
    daysJobs.remove(month)?.cancel()
    setDays(month, DaysState.Loading)
    daysJobs[month] = viewModelScope.launch {
      getRecordingDaysUseCase(camera, month).fold(
        onSuccess = { days -> setDays(month, DaysState.Loaded(days)) },
        onFailure = { e ->
          Log.d(DEBUG_TAG, "Listing the days of $camera $month failed: $e")
          setDays(month, DaysState.Failed(e.toHistoryError()))
        }
      )
    }
  }

  private fun collapse(month: YearMonth) {
    daysJobs.remove(month)?.cancel()
    setDays(month, DaysState.Collapsed)
  }

  private fun months(): List<MonthItem>? = (_uiState.value.content as? HistoryContent.Months)?.months

  private fun setContent(content: HistoryContent) = _uiState.update { it.copy(content = content) }

  private fun setDays(month: YearMonth, days: DaysState) = _uiState.update { state ->
    val content = state.content as? HistoryContent.Months ?: return@update state
    state.copy(
      content = content.copy(
        months = content.months.map { if (it.month == month) it.copy(days = days) else it }
      )
    )
  }
}
