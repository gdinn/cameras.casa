package com.gdisys.cameras.feature.config

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.ToastEventViewModel
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.usecase.GetVpnConfigStatusUseCase
import com.gdisys.cameras.core.storage.domain.usecase.ParseUserPreferencesFromQrCodeUseCase
import com.gdisys.cameras.core.storage.domain.usecase.SaveUserPreferencesUseCase
import com.gdisys.cameras.core.storage.domain.VpnDataUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
  @ApplicationContext private val context: Context
) : ToastEventViewModel() {
  val uiState: StateFlow<VpnDataUiState> = getVpnConfigStatusUseCase().stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    VpnDataUiState.Loading
  )
  private val _vpnPermissionUiEvent = Channel<VpnPermissionUiEvent>()
  val vpnPermissionUiEvent: Flow<VpnPermissionUiEvent> = _vpnPermissionUiEvent.receiveAsFlow()

  fun onQrCodeScanned(rawJson: String) {
      try {
        val userPreferences = parseUserPreferencesFromQrCodeUseCase(rawJson)
        if(userPreferences == null) {
          updateUserPreferences(UserPreferences())
          showToast(ConfigToastMessage.QR_CODE_INVALID_DATA_ERROR)
        } else {
          updateUserPreferences(userPreferences)
        }
      } catch (e: Exception) {
        Log.e(DEBUG_TAG, "QR Code scan error", e)
        updateUserPreferences(UserPreferences())
        showToast(ConfigToastMessage.QR_CODE_FORMAT_ERROR)
      }
  }

  fun showToast(configToastMessage: ConfigToastMessage) = showToast(configToastMessage.resId)

  fun updateUserPreferences(userPreferences: UserPreferences) {
    viewModelScope.launch {
      saveUserPreferencesUseCase(userPreferences)
    }
  }

  fun acceptVpnPermission() {
    requestVpnPermission(VpnService.prepare(context))
  }

  private fun requestVpnPermission(intent: Intent?) {
    if(intent != null) {
      viewModelScope.launch {
        _vpnPermissionUiEvent.send(VpnPermissionUiEvent.RequestPermission(intent))
      }
    } else {
      showToast(ConfigToastMessage.PERMISSION_ALREADY_GRANTED)
    }
  }
}
