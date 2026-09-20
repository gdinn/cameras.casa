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
import com.gdisys.cameras.core.webrtc.data.WhepConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
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
  private val whepConnectionManager: WhepConnectionManager
) : ToastEventViewModel() {
  private val _uiState: MutableStateFlow<HomeUiState> = MutableStateFlow(HomeUiState.Loading)
  val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

  private val _focusedStream = MutableStateFlow<String?>(null)

  /** Alimentada pela UI, que é quem enxerga a orientação real do dispositivo. */
  private val _orientation = MutableStateFlow(StreamOrientation.PORTRAIT)

  private val _navigateUiEvent = Channel<HomeNavigateUiEvent>()
  val navigateUiEvent = _navigateUiEvent.receiveAsFlow()

  private var exitConfirmationJob: Job? = null

  init {
    viewModelScope.launch {
      combine(
        getStreamPreferencesUseCase(),
        _orientation,
        _focusedStream,
        observeVpnStateUseCase()
      ) { preferences, orientation, focusedStream, vpn ->
        when {
          // Sem URLs não há nada a conectar: esperar pela VPN só mostraria um spinner perpétuo.
          preferences.streamUrls.isEmpty() -> HomeUiState.Empty

          vpn != VpnTunnelState.CONNECTED -> HomeUiState.Loading

          else -> HomeUiState.Ready(
            streams = preferences.orderFor(orientation),
            focusedStream = focusedStream,
            grid = preferences.gridFor(orientation),
            orientation = orientation
          )
        }
      }.collect {
        _uiState.value = it
      }
    }
  }

  fun connectStream(streamUrl: String, videoSink: VideoSink) {
    whepConnectionManager.connect(streamUrl, videoSink) {
      showToast(HomeToastMessage.STREAM_CONNECTION_ERROR)
    }
  }

  fun disconnectStream(streamUrl: String) {
    whepConnectionManager.disconnect(streamUrl)
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
   * Persiste a nova ordem — **somente** a da orientação corrente; a da outra permanece intacta.
   * Chamada quando o item é solto, não a cada movimento do arraste.
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

  fun connectVpn() {
    viewModelScope.launch {
      getVpnConfigUseCase().fold(
        onSuccess = { config ->
          connectVpnUseCase(config).onFailure { e ->
            Log.d(DEBUG_TAG, e.message.toString())
            showToast(HomeToastMessage.VPN_CONNECTION_ERROR)
            _navigateUiEvent.send(HomeNavigateUiEvent.ToConfig)
          }
        },
        onFailure = { e ->
          Log.d(DEBUG_TAG, e.message.toString())
          showToast(HomeToastMessage.VPN_CONNECTION_ERROR)
          _navigateUiEvent.send(HomeNavigateUiEvent.ToConfig)
        }
      )
    }
  }

  fun disconnectVpn() {
    viewModelScope.launch {
      disconnectVpnUseCase()
    }
  }

  override fun onCleared() {
    whepConnectionManager.closeAll()
    super.onCleared()
  }
}

sealed interface HomeNavigateUiEvent {
  data object ToConfig : HomeNavigateUiEvent
  data object ExitApp : HomeNavigateUiEvent
}
