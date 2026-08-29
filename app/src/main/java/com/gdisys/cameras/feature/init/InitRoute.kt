package com.gdisys.cameras.feature.init

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdisys.cameras.core.components.ToastDisplayer

@Composable
fun InitRoute(
  viewModel: InitViewModel,
  onNavigateToConfig: () -> Unit,
  onNavigateToHome: () -> Unit,
) {
  val context = LocalContext.current
  val resources = LocalResources.current

  LaunchedEffect(viewModel) {
    viewModel.navigateUiEvent.collect { state ->
      when(state) {
        NavigateUiEvent.ToConfig -> onNavigateToConfig()
        NavigateUiEvent.ToHome -> onNavigateToHome()
      }
    }
  }

  ToastDisplayer(
    viewModel,
    context = context,
    toastUiEvent = viewModel.uiEvent,
    resources = resources
  )

  InitScreen()
}