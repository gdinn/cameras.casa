package com.gdisys.cameras.feature.config

import android.app.Activity.RESULT_OK
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.storage.DataStoreManager
import com.gdisys.cameras.core.storage.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfigViewModel @Inject constructor(
  private val dataStoreManager: DataStoreManager
): ViewModel() {
  val uiState: StateFlow<ConfigUiState> = dataStoreManager.userPrefsState.map { prefs ->
    ConfigUiState(prefs.vpnConfigTokens?.pPsk?.isEmpty() ?: true)
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = ConfigUiState(vpnConfigTokensEmpty = true)
  )

  private val _uiEvent = Channel<ConfigUiEvent>()
  val uiEvent = _uiEvent.receiveAsFlow()

  fun showToast(message: String) {
    viewModelScope.launch {
      _uiEvent.send(ConfigUiEvent.ShowToast(message))
    }
  }

  fun updateUserPreferences(userPreferences: UserPreferences) {
    viewModelScope.launch {
      dataStoreManager.updateUserPreferences(userPreferences)
    }
  }
}

data class ConfigUiState(
  val vpnConfigTokensEmpty: Boolean = true
)

sealed interface ConfigUiEvent {
  data class ShowToast(val message: String) : ConfigUiEvent
}