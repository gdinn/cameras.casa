package com.gdisys.cameras.feature.cameras

import androidx.annotation.StringRes
import com.gdisys.cameras.R
import com.gdisys.cameras.core.ToastMessage

enum class HomeToastMessage(@StringRes override val resId: Int) : ToastMessage {
  VPN_CONNECTION_ERROR(R.string.home_route_vpn_connection_error),
  STREAM_CONNECTION_ERROR(R.string.home_route_stream_connection_error),
}
