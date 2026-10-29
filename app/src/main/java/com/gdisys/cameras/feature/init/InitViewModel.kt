package com.gdisys.cameras.feature.init

import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.ToastEventViewModel
import com.gdisys.cameras.core.storage.domain.VpnCredentialsStatus
import com.gdisys.cameras.core.storage.domain.usecase.GetVpnConfigStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
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
    // The gate decides once. The Loading entry stays in the back stack under Config, so a
    // continuous collect would keep reacting to credential changes made later on Config, sending
    // events nobody collects.
    viewModelScope.launch {
      val status = getVpnConfigStatusUseCase()
        .filterIsInstance<VpnCredentialsStatus.Loaded>()
        .firstOrNull() ?: return@launch
      if (status.hasValidCredentials) {
        _navigateUiEvent.send(NavigateUiEvent.ToHome)
      } else {
        showToast(InitToastMessage.CREDENTIALS_NOT_FOUND)
        _navigateUiEvent.send(NavigateUiEvent.ToConfig)
      }
    }
  }
}

sealed interface NavigateUiEvent {
  data object ToConfig: NavigateUiEvent
  data object ToHome: NavigateUiEvent
}