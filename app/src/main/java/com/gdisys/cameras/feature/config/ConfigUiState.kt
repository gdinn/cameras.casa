package com.gdisys.cameras.feature.config

data class ConfigUiState(
  val cameraPermissionButtonState: ConfigButtonState = ConfigButtonState.Loading,
  val qrCodeButtonState: ConfigButtonState = ConfigButtonState.Loading,
  val vpnPermissionButtonState: ConfigButtonState = ConfigButtonState.Loading,
  val streamURLsButtonState: ConfigButtonState = ConfigButtonState.Loading,
  val canNavigateBackToHome: Boolean = false
) {
  val isQrCodeButtonEnabled: Boolean
    get() = cameraPermissionButtonState == ConfigButtonState.Done

  /**
   * Primeiro requisito ainda não satisfeito para a Home funcionar, ou `null` quando todos estão.
   * A ordem é a do feedback esperado: credenciais → permissão de VPN → URLs.
   */
  val missingRequirement: ConfigRequirement?
    get() = when {
      qrCodeButtonState != ConfigButtonState.Done -> ConfigRequirement.VPN_CREDENTIALS
      vpnPermissionButtonState != ConfigButtonState.Done -> ConfigRequirement.VPN_PERMISSION
      streamURLsButtonState != ConfigButtonState.Done -> ConfigRequirement.STREAM_URLS
      else -> null
    }

  val canNavigateToHome: Boolean
    get() = missingRequirement == null
}

/** Requisitos que a Home precisa ter satisfeitos para ser exibida. */
enum class ConfigRequirement {
  VPN_CREDENTIALS,
  VPN_PERMISSION,
  STREAM_URLS
}

sealed interface ConfigButtonState {
  data object Loading: ConfigButtonState
  data object Ready: ConfigButtonState
  data object Done: ConfigButtonState
  data object Error: ConfigButtonState
}
