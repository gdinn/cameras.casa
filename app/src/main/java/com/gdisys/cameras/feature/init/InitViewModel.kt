package com.gdisys.cameras.feature.init

import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.components.ToastEventViewModel
import com.gdisys.cameras.core.storage.domain.VpnDataUiState
import com.gdisys.cameras.core.storage.domain.usecase.GetVpnConfigStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InitViewModel  @Inject constructor(
  getVpnConfigStatusUseCase: GetVpnConfigStatusUseCase
) : ToastEventViewModel() {
  private val _navigateUiEvent = Channel<NavigateUiEvent>()
  val navigateUiEvent = _navigateUiEvent.receiveAsFlow()

  init {
    viewModelScope.launch {
      getVpnConfigStatusUseCase().collect { state ->
        if (state !is VpnDataUiState.Success) return@collect
        if (state.vpnConfigTokensEmpty) {
          showToast(InitToastMessage.CREDENTIALS_NOT_FOUND)
          _navigateUiEvent.send(NavigateUiEvent.ToConfig)
        } else {
          _navigateUiEvent.send(NavigateUiEvent.ToHome)
        }
      }
    }
  }

  fun showToast(initToastMessage: InitToastMessage) = showToast(initToastMessage.resId)
}

sealed interface NavigateUiEvent {
  data object ToConfig: NavigateUiEvent
  data object ToHome: NavigateUiEvent
}