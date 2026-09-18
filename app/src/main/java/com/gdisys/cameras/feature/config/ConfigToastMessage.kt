package com.gdisys.cameras.feature.config

import androidx.annotation.StringRes
import com.gdisys.cameras.R
import com.gdisys.cameras.core.ToastMessage

enum class ConfigToastMessage(@StringRes override val resId: Int) : ToastMessage {
  VPN_PERMISSION_ACCEPTED(R.string.config_route_vpn_accepted),
  VPN_PERMISSION_DENIED(R.string.config_route_vpn_denied),
  PERMISSION_ALREADY_GRANTED(R.string.config_route_vpn_already_granted),
  CAMERA_PERMISSION_ALREADY_GRANTED(R.string.config_route_camera_already_granted),
  CAMERA_PERMISSION_PERMANENTLY_DENIED(R.string.config_route_camera_permanently_denied),
  CAMERA_PERMISSION_REQUIRED_FOR_QR_CODE(R.string.config_route_camera_permission_required_for_qr_code),
  QR_CODE_LOADED_SUCCESSFULLY(R.string.config_route_qr_code_loaded_successfully),
  QR_CODE_FORMAT_ERROR(R.string.qr_code_format_error),
  QR_CODE_INVALID_DATA_ERROR(R.string.qr_code_invalid_data_error),
  SAVE_PREFERENCES_ERROR(R.string.config_route_save_preferences_error),
  APP_CONFIGURATION_MISSING(R.string.config_route_app_configuration_missing),
}

