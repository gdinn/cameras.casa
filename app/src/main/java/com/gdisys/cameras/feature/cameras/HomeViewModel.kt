package com.gdisys.cameras.feature.cameras

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.storage.domain.usecase.GetUserPreferencesUseCase
import com.gdisys.cameras.core.storage.toVpnConfigOrNull
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.gdisys.cameras.core.vpn.domain.usecase.ConnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.DisconnectVpnUseCase
import com.gdisys.cameras.core.webrtc.domain.WhepClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject
import javax.inject.Provider

// TODO: Pegar via storage os endpoints finais -> http://[fd00:20::cafe] é padrão por conta do network_security_config
private val DEFAULT_CAMERA_STREAMS = listOf(
  "http://[fd00:20::cafe]:8889/cam_160",
  "http://[fd00:20::cafe]:8889/cam_161",
  "http://[fd00:20::cafe]:8889/cam_162",
  "http://[fd00:20::cafe]:8889/cam_163"
)

@HiltViewModel
class HomeViewModel @Inject constructor(
  vpnRepository: VpnRepository,
  private val connectVpnUseCase: ConnectVpnUseCase,
  private val disconnectVpnUseCase: DisconnectVpnUseCase,
  private val getUserPreferencesUseCase: GetUserPreferencesUseCase,
  private val whepClientProvider: Provider<WhepClient>,
  val eglBase: EglBase
) : ViewModel() {
  private val _uiState: MutableStateFlow<HomeUiState> = MutableStateFlow(HomeUiState.Loading)
  val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

  private val _streams = MutableStateFlow(DEFAULT_CAMERA_STREAMS)
  private val _focusedStream = MutableStateFlow<String?>(null)

  init {
    viewModelScope.launch {
      combine(
        _streams,
        _focusedStream,
        vpnRepository.vpnState
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

  suspend fun startWhepConnection(streamUrl: String, renderer: SurfaceViewRenderer): WhepClient? {
    try {
      val whepClient = whepClientProvider.get()
      whepClient.connect(streamUrl, renderer)
      return whepClient
    } catch (e: Exception) {
      Log.d(DEBUG_TAG, e.message.toString())
    }
    return null
  }

  fun onCreateRenderer(context: Context): SurfaceViewRenderer {
    return SurfaceViewRenderer(context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      init(eglBase.eglBaseContext, null)
      setMirror(false)
      setEnableHardwareScaler(true)
    }
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
      try {
        getUserPreferencesUseCase().first().toVpnConfigOrNull()?.let { config ->
          connectVpnUseCase(config)
        }
      } catch (e: Exception) {
        Log.d(DEBUG_TAG, e.message.toString())
      }
    }
  }

  fun disconnectVpn() {
    viewModelScope.launch {
      disconnectVpnUseCase()
    }
  }
}
