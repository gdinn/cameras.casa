package com.gdisys.cameras.feature.init
import androidx.annotation.StringRes
import com.gdisys.cameras.R

enum class InitToastMessage(@StringRes val resId: Int) {
  CREDENTIALS_NOT_FOUND(R.string.init_screen_no_credentials)
}