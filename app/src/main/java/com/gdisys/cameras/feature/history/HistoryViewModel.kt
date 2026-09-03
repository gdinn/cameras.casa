package com.gdisys.cameras.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.vpn.domain.VpnSessionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
  private val vpnSessionCoordinator: VpnSessionCoordinator
) : ViewModel() {

  fun connectVpn() {
    viewModelScope.launch {
      vpnSessionCoordinator.connect()
    }
  }

  fun disconnectVpn() {
    viewModelScope.launch {
      vpnSessionCoordinator.disconnect()
    }
  }
}
