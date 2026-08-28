package com.gdisys.cameras.feature.config

import androidx.annotation.StringRes
import com.gdisys.cameras.R

enum class ConfigToastMessage(@StringRes val resId: Int) {
  VPN_PERMISSION_ACCEPTED(R.string.config_route_vpn_accepted),
  PERMISSION_ALREADY_GRANTED(R.string.config_route_vpn_already_granted),
  QR_CODE_FORMAT_ERROR(R.string.qr_code_format_error),
  QR_CODE_INVALID_DATA_ERROR(R.string.qr_code_invalid_data_error),
}

