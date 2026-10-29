package com.gdisys.cameras.feature.cameras

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.ToastEventViewModel
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.core.storage.domain.model.gridFor
import com.gdisys.cameras.core.storage.domain.model.orderFor
import com.gdisys.cameras.core.storage.domain.usecase.GetStreamPreferencesUseCase
import com.gdisys.cameras.core.storage.domain.usecase.GetVpnConfigUseCase
import com.gdisys.cameras.core.storage.domain.usecase.UpdateStreamOrderUseCase
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.gdisys.cameras.core.vpn.domain.usecase.ConnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.DisconnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.ObserveVpnStateUseCase
import com.gdisys.cameras.core.webrtc.StreamConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.VideoSink
import javax.inject.Inject

private const val EXIT_CONFIRMATION_WINDOW_MS = 2000L

@HiltViewModel
class HomeViewModel @Inject constructor(
  observeVpnStateUseCase: ObserveVpnStateUseCase,
  getStreamPreferencesUseCase: GetStreamPreferencesUseCase,
  private val updateStreamOrderUseCase: UpdateStreamOrderUseCase,
  private val connectVpnUseCase: ConnectVpnUseCase,
  private val disconnectVpnUseCase: DisconnectVpnUseCase,
  private val getVpnConfigUseCase: GetVpnConfigUseCase,
  private val streamConnectionRepository: StreamConnectionRepository
) : ToastEventViewModel() {
  private val _uiState: MutableStateFlow<HomeUiState> = MutableStateFlow(HomeUiState.Loading)
  val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

  private val _focusedStream = MutableStateFlow<String?>(null)

  /** URLs whose last connection attempt failed. A new attempt, or leaving the screen, clears one. */
  private val _failedStreams = MutableStateFlow<Set<String>>(emptySet())

  /** Fed by the UI, which is the only layer that can see the real device orientation. */
  private val _orientation = MutableStateFlow(StreamOrientation.PORTRAIT)

  private val _navigateUiEvent = Channel<HomeNavigateUiEvent>()
  val navigateUiEvent = _navigateUiEvent.receiveAsFlow()

  private var exitConfirmationJob: Job? = null

  /**
   * Whether the route is resumed, and so whether the tunnel should be up. `null` until the first
   * lifecycle event.
   */
  private val _tunnelWanted = MutableStateFlow<Boolean?>(null)

  init {
    viewModelScope.launch {
      combine(
        getStreamPreferencesUseCase(),
        _orientation,
        _focusedStream,
        _failedStreams,
        observeVpnStateUseCase()
      ) { preferences, orientation, focusedStream, failedStreams, vpn ->
        when {
          // With no URLs there is nothing to connect to, and waiting on the VPN would show a
          // spinner that never resolves. This case therefore comes before the tunnel state.
          preferences.streamUrls.isEmpty() -> HomeUiState.Empty

          vpn != VpnTunnelState.CONNECTED -> HomeUiState.Loading

          else -> {
            val streams = preferences.orderFor(orientation)
            HomeUiState.Ready(
              streams = streams,
              focusedStream = focusedStream,
              grid = preferences.gridFor(orientation),
              orientation = orientation,
              failedStreams = failedStreams intersect streams.toSet()
            )
          }
        }
      }.collect {
        _uiState.value = it
      }
    }

    // A single collector applies the lifecycle to the tunnel, so connect and disconnect never
    // overlap and the last state always wins: StateFlow drops the intermediate states of a quick
    // pause/resume, and the ones equal to what was last applied. Not collectLatest, which would
    // cancel a native setState halfway.
    viewModelScope.launch {
      var tunnelRequested = false
      try {
        _tunnelWanted.filterNotNull().collect { wanted ->
          tunnelRequested = wanted
          if (wanted) connectVpn() else disconnectVpn()
        }
      } finally {
        // Cleared with a connect as the last operation: the pause arrived while it ran and was
        // never applied. Home is the only owner of the tunnel, so it goes down with it.
        if (tunnelRequested) disconnectVpn()
      }
    }
  }

  /** Also the retry: the card's "Try again" disconnects and calls this again with the same sink. */
  fun connectStream(streamUrl: String, videoSink: VideoSink) {
    _failedStreams.update { it - streamUrl }
    streamConnectionRepository.connect(streamUrl, videoSink) {
      _failedStreams.update { it + streamUrl }
      showToast(HomeToastMessage.STREAM_CONNECTION_ERROR)
    }
  }

  fun disconnectStream(streamUrl: String) {
    streamConnectionRepository.disconnect(streamUrl)
    _failedStreams.update { it - streamUrl }
  }

  fun focusStream(url: String) {
    _focusedStream.value = url
  }

  fun clearFocusedStream() {
    _focusedStream.value = null
  }

  fun onOrientationChanged(orientation: StreamOrientation) {
    _orientation.value = orientation
  }

  /**
   * Persists the new order — **only** the current orientation's; the other one is left intact.
   * Called when the item is dropped, not on every movement of the drag.
   */
  fun onStreamsReordered(newOrder: List<String>) {
    viewModelScope.launch {
      updateStreamOrderUseCase(_orientation.value, newOrder).onFailure { e ->
        Log.e(DEBUG_TAG, "Failed to persist the stream order", e)
        showToast(HomeToastMessage.STREAM_ORDER_SAVE_ERROR)
      }
    }
  }

  fun onBackPressed() {
    val pendingExitConfirmation = exitConfirmationJob
    if (pendingExitConfirmation != null) {
      pendingExitConfirmation.cancel()
      exitConfirmationJob = null
      viewModelScope.launch { _navigateUiEvent.send(HomeNavigateUiEvent.ExitApp) }
    } else {
      showToast(HomeToastMessage.PRESS_BACK_AGAIN_TO_EXIT)
      exitConfirmationJob = viewModelScope.launch {
        delay(EXIT_CONFIRMATION_WINDOW_MS)
        exitConfirmationJob = null
      }
    }
  }

  fun onResumed() {
    _tunnelWanted.value = true
  }

  fun onPaused() {
    _tunnelWanted.value = false
  }

  private suspend fun connectVpn() {
    // NonCancellable: an operation that started finishes even if the ViewModel is cleared, so the
    // tunnel is never left halfway.
    val result = withContext(NonCancellable) {
      getVpnConfigUseCase().mapCatching { config -> connectVpnUseCase(config).getOrThrow() }
    }
    result.onFailure { e ->
      Log.d(DEBUG_TAG, e.message.toString())
      showToast(HomeToastMessage.VPN_CONNECTION_ERROR)
      // Sent apart, so the tunnel collector does not wait for the route to collect the event.
      viewModelScope.launch { _navigateUiEvent.send(HomeNavigateUiEvent.ToConfig) }
    }
  }

  private suspend fun disconnectVpn() {
    withContext(NonCancellable) { disconnectVpnUseCase() }
  }

  override fun onCleared() {
    streamConnectionRepository.closeAll()
    super.onCleared()
  }
}

sealed interface HomeNavigateUiEvent {
  data object ToConfig : HomeNavigateUiEvent
  data object ExitApp : HomeNavigateUiEvent
}
