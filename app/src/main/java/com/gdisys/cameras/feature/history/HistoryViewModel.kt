package com.gdisys.cameras.feature.history

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.ToastEventViewModel
import com.gdisys.cameras.core.vpn.domain.VpnSessionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
  private val vpnSessionCoordinator: VpnSessionCoordinator
) : ToastEventViewModel() {

  fun connectVpn() {
    viewModelScope.launch {
      vpnSessionCoordinator.connect().onFailure { e ->
        Log.d(DEBUG_TAG, e.message.toString())
        showToast(HistoryToastMessage.VPN_CONNECTION_ERROR)
      }
    }
  }

  fun disconnectVpn() {
    viewModelScope.launch {
      vpnSessionCoordinator.disconnect()
    }
  }
}
