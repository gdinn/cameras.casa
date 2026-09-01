package com.gdisys.cameras.feature.config

import android.app.Activity.RESULT_OK
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdisys.cameras.core.components.ToastDisplayer
import com.gdisys.cameras.feature.config.components.ConfigScreen

@Composable
fun ConfigRoute(
  viewModel: ConfigViewModel,
  onNavigateToHome: () -> Unit,
  onQrCodeScanned: (String) -> Unit
) {
  val context = LocalContext.current
  val resources = LocalResources.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  val vpnLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == RESULT_OK) {
      viewModel.showToast(ConfigToastMessage.VPN_PERMISSION_ACCEPTED)
    }
  }

  LaunchedEffect(viewModel) {
    viewModel.vpnPermissionUiEvent.collect { event ->
      when(event) {
        is VpnPermissionUiEvent.RequestPermission -> {
          vpnLauncher.launch(event.intent)
        }
      }
    }
  }

  ToastDisplayer(
    viewModel,
    toastUiEvent = viewModel.uiEvent,
    context = context,
    resources = resources
  )

  ConfigScreen(
    uiState = uiState,
    acceptVpnPermission = {
      viewModel.requestVpnPermission(VpnService.prepare(context))
    },
    onNavigateToHome = onNavigateToHome,
    onQrCodeScanned = onQrCodeScanned
  )
}
