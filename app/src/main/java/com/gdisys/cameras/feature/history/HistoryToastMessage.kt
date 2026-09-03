package com.gdisys.cameras.feature.history

import androidx.annotation.StringRes
import com.gdisys.cameras.R
import com.gdisys.cameras.core.ToastMessage

enum class HistoryToastMessage(@StringRes override val resId: Int) : ToastMessage {
  VPN_CONNECTION_ERROR(R.string.history_route_vpn_connection_error),
}
