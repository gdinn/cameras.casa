package com.gdisys.cameras.feature.config

import android.content.Intent
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.ToastEventViewModel
import com.gdisys.cameras.core.permission.domain.usecase.HasCameraPermissionUseCase
import com.gdisys.cameras.core.permission.domain.usecase.HasVpnPermissionUseCase
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

const val QR_CODE_RESULT_KEY = "qr_code_raw_json"

sealed interface VpnPermissionUiEvent {
  data class RequestPermission(val intent: Intent): VpnPermissionUiEvent
}

@HiltViewModel
class ConfigViewModel @Inject constructor(
  private val saveUserPreferencesUseCase: SaveUserPreferencesUseCase,
  private val parseUserPreferencesFromQrCodeUseCase: ParseUserPreferencesFromQrCodeUseCase,
  getVpnConfigStatusUseCase: GetVpnConfigStatusUseCase,
  hasVpnPermissionUseCase: HasVpnPermissionUseCase,
  hasCameraPermissionUseCase: HasCameraPermissionUseCase,
  private val requestVpnPermissionUseCase: RequestVpnPermissionUseCase
) : ToastEventViewModel() {

  private val _cameraPermissionButtonState = MutableStateFlow(
    if (hasCameraPermissionUseCase()) ConfigButtonState.Done else ConfigButtonState.Ready
  )
  private val _vpnPermissionButtonState = MutableStateFlow(
    if (hasVpnPermissionUseCase()) ConfigButtonState.Done else ConfigButtonState.Ready
  )
  private val _qrCodeError = MutableStateFlow(false)

  val uiState: StateFlow<ConfigUiState> = combine(
    _cameraPermissionButtonState,
    getVpnConfigStatusUseCase(),
    _vpnPermissionButtonState,
    _qrCodeError
  ) { cameraPermissionButtonState, vpnConfigStatus, vpnPermissionButtonState, qrCodeError ->
    ConfigUiState(
      cameraPermissionButtonState = cameraPermissionButtonState,
      qrCodeButtonState = when {
        qrCodeError -> ConfigButtonState.Error
        vpnConfigStatus is VpnCredentialsStatus.Loading -> ConfigButtonState.Loading
        vpnConfigStatus is VpnCredentialsStatus.Loaded && vpnConfigStatus.hasValidCredentials -> ConfigButtonState.Done
        else -> ConfigButtonState.Ready
      },
      vpnPermissionButtonState = vpnPermissionButtonState,
      streamURLsButtonState = ConfigButtonState.Done
    )
  }.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    ConfigUiState()
  )

  private val _vpnPermissionUiEvent = Channel<VpnPermissionUiEvent>()
  val vpnPermissionUiEvent: Flow<VpnPermissionUiEvent> = _vpnPermissionUiEvent.receiveAsFlow()

  private val _requestCameraPermissionEvent = Channel<Unit>()
  val requestCameraPermissionEvent: Flow<Unit> = _requestCameraPermissionEvent.receiveAsFlow()

  private val _navigateToScannerEvent = Channel<Unit>()
  val navigateToScannerEvent: Flow<Unit> = _navigateToScannerEvent.receiveAsFlow()

  fun onShowScanner() {
    if (_cameraPermissionButtonState.value != ConfigButtonState.Done) return
    viewModelScope.launch {
      _navigateToScannerEvent.send(Unit)
    }
  }

  fun onRequestCameraPermission() {
    viewModelScope.launch {
      _requestCameraPermissionEvent.send(Unit)
    }
  }

  fun onCameraPermissionResult(granted: Boolean) {
    _cameraPermissionButtonState.value = if (granted) ConfigButtonState.Done else ConfigButtonState.Ready
  }

  fun onVpnPermissionAccepted() {
    _vpnPermissionButtonState.value = ConfigButtonState.Done
  }

  fun onQrCodeScanned(rawJson: String) {
    _qrCodeError.value = false
    parseUserPreferencesFromQrCodeUseCase(rawJson).fold(
      onSuccess = { userPreferences ->
        if (userPreferences == null) {
          _qrCodeError.value = true
          updateUserPreferences(UserPreferences())
          showToast(ConfigToastMessage.QR_CODE_INVALID_DATA_ERROR)
        } else {
          updateUserPreferences(userPreferences)
        }
      },
      onFailure = { e ->
        Log.e(DEBUG_TAG, "QR Code scan error", e)
        _qrCodeError.value = true
        updateUserPreferences(UserPreferences())
        showToast(ConfigToastMessage.QR_CODE_FORMAT_ERROR)
      }
    )
  }

  fun updateUserPreferences(userPreferences: UserPreferences) {
    viewModelScope.launch {
      saveUserPreferencesUseCase(userPreferences).onFailure { e ->
        Log.e(DEBUG_TAG, "Failed to save user preferences", e)
        _qrCodeError.value = true
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
