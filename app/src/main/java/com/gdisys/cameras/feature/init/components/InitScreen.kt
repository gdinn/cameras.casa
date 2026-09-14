package com.gdisys.cameras.feature.init.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gdisys.cameras.R
import com.gdisys.cameras.core.components.LoadingScreen

@Composable
fun InitScreen() {
  LoadingScreen(stringResource(R.string.loading_screen_verifying_credentials))
}
