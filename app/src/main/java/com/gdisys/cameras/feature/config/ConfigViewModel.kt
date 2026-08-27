package com.gdisys.cameras.feature.config

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.R
import com.gdisys.cameras.core.storage.DataStoreManager
import com.gdisys.cameras.core.storage.UserPreferences
import com.gdisys.cameras.core.storage.isInvalid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfigViewModel @Inject constructor(
  private val dataStoreManager: DataStoreManager
): ViewModel() {
  val uiState: StateFlow<VpnDataUiState> = dataStoreManager.userPrefsState.map { prefs ->
    VpnDataUiState.Success(
      vpnConfigTokensEmpty =
        prefs.vpnConfigTokens?.isInvalid() ?: true ||
        prefs.vpnConfigDefaults?.isInvalid() ?: true
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = VpnDataUiState.Loading
  )

  private val _uiEvent = Channel<ConfigUiEvent>()
  val uiEvent = _uiEvent.receiveAsFlow()

  fun showToast(configToastMessage: ConfigToastMessage) {
    viewModelScope.launch {
      _uiEvent.send(ConfigUiEvent.ShowToast(configToastMessage))
    }
  }

  fun updateUserPreferences(userPreferences: UserPreferences) {
    viewModelScope.launch {
      dataStoreManager.updateUserPreferences(userPreferences)
    }
  }
}

sealed interface VpnDataUiState {
  data object Loading : VpnDataUiState // Estado inicial real e semântico
  data class Success(val vpnConfigTokensEmpty: Boolean) : VpnDataUiState
}

sealed interface ConfigUiEvent {
  data class ShowToast(val configToastMessage: ConfigToastMessage): ConfigUiEvent
}
