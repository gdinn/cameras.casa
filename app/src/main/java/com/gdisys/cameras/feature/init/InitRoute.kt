package com.gdisys.cameras.feature.init

import androidx.compose.runtime.Composable
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
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  ToastDisplayer(
    viewModel,
    context = context,
    toastUiEvent = viewModel.uiEvent,
    resources = resources
  )

  InitScreen(
    uiState,
    showToast = { initToastMessage ->
      viewModel.showToast(initToastMessage)
    },
    onNavigateToConfig = onNavigateToConfig,
    onNavigateToHome = onNavigateToHome
  )
}