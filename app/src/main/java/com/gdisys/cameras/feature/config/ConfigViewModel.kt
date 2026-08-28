package com.gdisys.cameras.feature.config

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.components.ToastUiEvent
import com.gdisys.cameras.core.storage.DataStoreManager
import com.gdisys.cameras.core.storage.UserPreferences
import com.gdisys.cameras.core.storage.isValid
import com.gdisys.cameras.core.vpn.domain.VpnConfigStatusProvider
import com.gdisys.cameras.core.vpn.domain.VpnDataUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class ConfigViewModel @Inject constructor(
  private val dataStoreManager: DataStoreManager,
  vpnConfigStatusProvider: VpnConfigStatusProvider
): ViewModel() {
  val uiState: StateFlow<VpnDataUiState> = vpnConfigStatusProvider.observe(viewModelScope)

  private val _toastUiEvent = Channel<ToastUiEvent>()
  val uiEvent = _toastUiEvent.receiveAsFlow()

  fun onQrCodeScanned(rawJson: String) {
      try {
        val userPreferences = parseJsonToUserPreferences(rawJson)
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

  private fun parseJsonToUserPreferences(rawJson: String): UserPreferences? {
    val sanitizedJson = rawJson
      .trim()
      .replace("\uFEFF", "")
    val decoded = Json.decodeFromString<UserPreferences>(sanitizedJson)
    return if (decoded.vpnConfigDefaults?.isValid() == true && decoded.vpnConfigTokens?.isValid() == true) {
      decoded
    } else {
      null
    }
  }

  fun showToast(configToastMessage: ConfigToastMessage) {
    viewModelScope.launch {
      _toastUiEvent.send(ToastUiEvent.Show(configToastMessage.resId))
    }
  }

  fun updateUserPreferences(userPreferences: UserPreferences) {
    viewModelScope.launch {
      dataStoreManager.updateUserPreferences(userPreferences)
    }
  }
}
