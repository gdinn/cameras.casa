package com.gdisys.cameras.feature.init
import androidx.annotation.StringRes
import com.gdisys.cameras.R
import com.gdisys.cameras.core.ToastMessage

enum class InitToastMessage(@StringRes override val resId: Int) : ToastMessage {
  CREDENTIALS_NOT_FOUND(R.string.init_screen_no_credentials)
}