package com.gdisys.cameras.feature.config

data class ConfigUiState(
  val cameraPermissionButtonState: ConfigButtonState = ConfigButtonState.Loading,
  val qrCodeButtonState: ConfigButtonState = ConfigButtonState.Loading,
  val vpnPermissionButtonState: ConfigButtonState = ConfigButtonState.Loading,
  val streamURLsButtonState: ConfigButtonState = ConfigButtonState.Loading
) {
  val isQrCodeButtonEnabled: Boolean
    get() = cameraPermissionButtonState == ConfigButtonState.Done
}

sealed interface ConfigButtonState {
  data object Loading: ConfigButtonState
  data object Ready: ConfigButtonState
  data object Done: ConfigButtonState
  data object Error: ConfigButtonState
}