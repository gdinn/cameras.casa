package com.gdisys.cameras.feature.init

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.components.ToastUiEvent
import com.gdisys.cameras.core.vpn.domain.VpnConfigStatusProvider
import com.gdisys.cameras.core.vpn.domain.VpnDataUiState
import com.gdisys.cameras.feature.config.ConfigToastMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InitViewModel  @Inject constructor(
  vpnConfigStatusProvider: VpnConfigStatusProvider
) : ViewModel(){
  val uiState: StateFlow<VpnDataUiState> = vpnConfigStatusProvider.observe(viewModelScope)

  private val _toastUiEvent = Channel<ToastUiEvent>()
  val uiEvent = _toastUiEvent.receiveAsFlow()

  fun showToast(initToastMessage: InitToastMessage) {
    viewModelScope.launch {
      _toastUiEvent.send(ToastUiEvent.Show(initToastMessage.resId))
    }
  }

}
