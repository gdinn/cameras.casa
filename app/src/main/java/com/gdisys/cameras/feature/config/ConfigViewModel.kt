package com.gdisys.cameras.feature.config

import android.content.Intent
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.ToastEventViewModel
import com.gdisys.cameras.core.storage.domain.VpnCredentialsStatus
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.usecase.GetVpnConfigStatusUseCase
import com.gdisys.cameras.core.storage.domain.usecase.ParseUserPreferencesFromQrCodeUseCase
import com.gdisys.cameras.core.storage.domain.usecase.SaveUserPreferencesUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.RequestVpnPermissionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface VpnPermissionUiEvent {
  data class RequestPermission(val intent: Intent): VpnPermissionUiEvent
}

@HiltViewModel
class ConfigViewModel @Inject constructor(
  private val saveUserPreferencesUseCase: SaveUserPreferencesUseCase,
  private val parseUserPreferencesFromQrCodeUseCase: ParseUserPreferencesFromQrCodeUseCase,
  getVpnConfigStatusUseCase: GetVpnConfigStatusUseCase,
  private val requestVpnPermissionUseCase: RequestVpnPermissionUseCase
) : ToastEventViewModel() {
  private val _showScanner = MutableStateFlow(false)

  val uiState: StateFlow<ConfigUiState> = combine(
    getVpnConfigStatusUseCase(),
    _showScanner
  ) { status, showScanner ->
    when {
      status is VpnCredentialsStatus.Loading -> ConfigUiState.Loading
      showScanner -> ConfigUiState.Scanning
      status is VpnCredentialsStatus.Loaded && status.hasValidCredentials -> ConfigUiState.ConfigurationLoaded
      else -> ConfigUiState.NeedsConfiguration
    }
  }.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    ConfigUiState.Loading
  )
  private val _vpnPermissionUiEvent = Channel<VpnPermissionUiEvent>()
  val vpnPermissionUiEvent: Flow<VpnPermissionUiEvent> = _vpnPermissionUiEvent.receiveAsFlow()

  fun onShowScanner() {
    _showScanner.value = true
  }

  fun onQrCodeScanned(rawJson: String) {
      _showScanner.value = false
      parseUserPreferencesFromQrCodeUseCase(rawJson).fold(
        onSuccess = { userPreferences ->
          if (userPreferences == null) {
            updateUserPreferences(UserPreferences())
            showToast(ConfigToastMessage.QR_CODE_INVALID_DATA_ERROR)
          } else {
            updateUserPreferences(userPreferences)
          }
        },
        onFailure = { e ->
          Log.e(DEBUG_TAG, "QR Code scan error", e)
          updateUserPreferences(UserPreferences())
          showToast(ConfigToastMessage.QR_CODE_FORMAT_ERROR)
        }
      )
  }

  fun updateUserPreferences(userPreferences: UserPreferences) {
    viewModelScope.launch {
      saveUserPreferencesUseCase(userPreferences).onFailure { e ->
        Log.e(DEBUG_TAG, "Failed to save user preferences", e)
        showToast(ConfigToastMessage.SAVE_PREFERENCES_ERROR)
      }
    }
  }

  fun acceptVpnPermission() {
    handleVpnPermissionIntent(requestVpnPermissionUseCase())
  }

  private fun handleVpnPermissionIntent(intent: Intent?) {
    if(intent != null) {
      viewModelScope.launch {
        _vpnPermissionUiEvent.send(VpnPermissionUiEvent.RequestPermission(intent))
      }
    } else {
      showToast(ConfigToastMessage.PERMISSION_ALREADY_GRANTED)
    }
  }
}
