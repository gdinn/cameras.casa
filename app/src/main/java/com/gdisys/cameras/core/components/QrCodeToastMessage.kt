package com.gdisys.cameras.core.components

import androidx.annotation.StringRes
import com.gdisys.cameras.R
import com.gdisys.cameras.core.ToastMessage

enum class QrCodeToastMessage(@StringRes override val resId: Int) : ToastMessage {
  CAMERA_INIT_ERROR(R.string.qr_code_route_camera_init_error),
  READING_CANCELLED(R.string.qr_code_route_reading_cancelled),
}
