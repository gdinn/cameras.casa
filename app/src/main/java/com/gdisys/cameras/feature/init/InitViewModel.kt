package com.gdisys.cameras.feature.init

import androidx.lifecycle.ViewModel
import com.gdisys.cameras.core.storage.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class InitViewModel @Inject constructor(
  private val dataStoreManager: DataStoreManager
): ViewModel() {


}

data class ConfigUiState(
  val vpnConfigTokensEmpty: Boolean = true
)

sealed interface ConfigUiEvent {
  data class ShowToast(val message: String) : ConfigUiEvent
}