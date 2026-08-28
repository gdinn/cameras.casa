package com.gdisys.cameras.feature.cameras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.storage.DataStoreManager
import com.gdisys.cameras.core.storage.toVpnConfigOrNull
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import com.gdisys.cameras.core.vpn.domain.usecase.ConnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.DisconnectVpnUseCase
import com.wireguard.android.backend.Tunnel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
  vpnRepository: VpnRepository,
  private val connectVpnUseCase: ConnectVpnUseCase,
  private val disconnectVpnUseCase: DisconnectVpnUseCase,
  private val dataStoreManager: DataStoreManager
) : ViewModel() {
  val vpnState: StateFlow<Tunnel.State> = vpnRepository.vpnState

  private val _isConnecting = MutableStateFlow(false)
  val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

  private val _streams = MutableStateFlow(DEFAULT_CAMERA_STREAMS)
  val streams: StateFlow<List<String>> = _streams.asStateFlow()

  private val _focusedStream = MutableStateFlow<String?>(null)
  val focusedStream: StateFlow<String?> = _focusedStream.asStateFlow()

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
      _isConnecting.value = true
      try {
        dataStoreManager.userPrefsState.first().toVpnConfigOrNull()?.let { config ->
          connectVpnUseCase(config)
        }
      } finally {
        _isConnecting.value = false
      }
    }
  }

  fun disconnectVpn() {
    viewModelScope.launch {
      disconnectVpnUseCase()
    }
  }
}
