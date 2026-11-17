package com.gdisys.cameras.feature.config

/**
 * @property notificationPermissionButtonState the optional notifications step, `null` when it is
 *   not shown: only admins download, and only Android 13+ asks. [ConfigButtonState.Error] means the
 *   user declined it, which is final.
 */
data class ConfigUiState(
  val cameraPermissionButtonState: ConfigButtonState = ConfigButtonState.Loading,
  val qrCodeButtonState: ConfigButtonState = ConfigButtonState.Loading,
  val vpnPermissionButtonState: ConfigButtonState = ConfigButtonState.Loading,
  val streamURLsButtonState: ConfigButtonState = ConfigButtonState.Loading,
  val canNavigateBackToHome: Boolean = false,
  val notificationPermissionButtonState: ConfigButtonState? = null
) {
  val isQrCodeButtonEnabled: Boolean
    get() = cameraPermissionButtonState == ConfigButtonState.Done

  /**
   * First requirement Home still needs, or `null` when they are all met. The order is the one the
   * feedback should follow: credentials -> VPN permission -> URLs.
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

/** Requirements Home needs met before it can be shown. */
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
