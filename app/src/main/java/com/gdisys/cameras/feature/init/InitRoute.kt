package com.gdisys.cameras.feature.init

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.gdisys.cameras.core.components.ToastDisplayer
import com.gdisys.cameras.feature.init.components.InitScreen

@Composable
fun InitRoute(
  viewModel: InitViewModel,
  onNavigateToConfig: () -> Unit,
  onNavigateToHome: () -> Unit,
) {
  LaunchedEffect(viewModel) {
    viewModel.navigateUiEvent.collect { state ->
      when(state) {
        NavigateUiEvent.ToConfig -> onNavigateToConfig()
        NavigateUiEvent.ToHome -> onNavigateToHome()
      }
    }
  }

  ToastDisplayer(
    toastUiEvent = viewModel.uiEvent
  )

  InitScreen()
}
