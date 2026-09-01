package com.gdisys.cameras.feature.cameras

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.ToastEventViewModel
import com.gdisys.cameras.core.storage.domain.usecase.GetVpnConfigUseCase
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.gdisys.cameras.core.vpn.domain.usecase.ConnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.DisconnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.ObserveVpnStateUseCase
import com.gdisys.cameras.core.webrtc.data.WhepConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.VideoSink
import javax.inject.Inject

// TODO: Pegar via storage os endpoints finais -> http://[fd00:20::cafe] é padrão por conta do network_security_config
private val DEFAULT_CAMERA_STREAMS = listOf(
  "http://[fd00:20::cafe]:8889/cam_160",
  "http://[fd00:20::cafe]:8889/cam_161",
  "http://[fd00:20::cafe]:8889/cam_162",
  "http://[fd00:20::cafe]:8889/cam_163"
)

@HiltViewModel
class HomeViewModel @Inject constructor(
  observeVpnStateUseCase: ObserveVpnStateUseCase,
  private val connectVpnUseCase: ConnectVpnUseCase,
  private val disconnectVpnUseCase: DisconnectVpnUseCase,
  private val getVpnConfigUseCase: GetVpnConfigUseCase,
  private val whepConnectionManager: WhepConnectionManager
) : ToastEventViewModel() {
  private val _uiState: MutableStateFlow<HomeUiState> = MutableStateFlow(HomeUiState.Loading)
  val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

  private val _streams = MutableStateFlow(DEFAULT_CAMERA_STREAMS)
  private val _focusedStream = MutableStateFlow<String?>(null)

  init {
    viewModelScope.launch {
      combine(
        _streams,
        _focusedStream,
        observeVpnStateUseCase()
      ) { streams, focusedStream, vpn ->
        if (vpn != VpnTunnelState.CONNECTED) {
          return@combine HomeUiState.Loading
        } else {
          return@combine HomeUiState.Ready(
            streams = streams,
            focusedStream = focusedStream
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

  fun moveStreamUp(index: Int) {
    moveStream(index, index - 1)
  }

  fun moveStreamDown(index: Int) {
    moveStream(index, index + 1)
  }

  private fun moveStream(fromIndex: Int, toIndex: Int) {
    _streams.update { current ->
      if (toIndex !in current.indices) return@update current
      current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }
  }

  fun connectVpn() {
    viewModelScope.launch {
      getVpnConfigUseCase().fold(
        onSuccess = { config ->
          connectVpnUseCase(config).onFailure { e ->
            Log.d(DEBUG_TAG, e.message.toString())
            showToast(HomeToastMessage.VPN_CONNECTION_ERROR)
          }
        },
        onFailure = { e ->
          Log.d(DEBUG_TAG, e.message.toString())
          showToast(HomeToastMessage.VPN_CONNECTION_ERROR)
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
