package com.gdisys.cameras.feature.init

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdisys.cameras.core.components.ToastDisplayer
import com.gdisys.cameras.feature.config.ConfigViewModel

@Composable
fun InitRoute(
  viewModel: ConfigViewModel,
  onNavigateToConfig: () -> Unit,
  onNavigateToHome: () -> Unit,
) {
  val context = LocalContext.current
  val resources = LocalResources.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  ToastDisplayer(
    viewModel,
    context,
    resources
  )

  InitScreen(
    uiState,
    showToast = { configToastMessage ->
      viewModel.showToast(configToastMessage)
    },
    onNavigateToConfig = onNavigateToConfig,
    onNavigateToHome = onNavigateToHome
  )
}