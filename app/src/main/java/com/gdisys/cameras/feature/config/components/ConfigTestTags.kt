package com.gdisys.cameras.feature.config.components

/**
 * Semantic tags shared by [ConfigScreen] and its instrumented tests.
 *
 * Tests find nodes by tag rather than by text because the screen is translated (`values-en`,
 * `values-pt-rBR`), so a text lookup would tie a test to the device locale.
 */
object ConfigTestTags {
  const val CAMERA_PERMISSION_STEP = "config_camera_permission_step"
  const val QR_CODE_STEP = "config_qr_code_step"
  const val VPN_PERMISSION_STEP = "config_vpn_permission_step"
  const val STREAM_URLS_STEP = "config_stream_urls_step"
  const val NAVIGATE_TO_HOME_BUTTON = "config_navigate_to_home_button"
}
