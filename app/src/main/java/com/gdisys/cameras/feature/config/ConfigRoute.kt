package com.gdisys.cameras.feature.config

import android.app.Activity.RESULT_OK
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdisys.cameras.core.components.ToastDisplayer
import com.gdisys.cameras.feature.config.components.ConfigScreen

@Composable
fun ConfigRoute(viewModel: ConfigViewModel) {
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

  fun acceptVpnPermission() {
    val intent = VpnService.prepare(context)
    if (intent != null) {
      vpnLauncher.launch(intent)
    } else {
      viewModel.showToast(ConfigToastMessage.PERMISSION_ALREADY_GRANTED)
    }
  }
  ToastDisplayer(
    viewModel,
    context,
    resources
  )

  ConfigScreen(
    uiState = uiState,
    showToast = { configToastMessage ->
      viewModel.showToast(configToastMessage)
    },
    updateUserPreferences = { userPreferences ->
      viewModel.updateUserPreferences(userPreferences)
    },
    acceptVpnPermission = {
      acceptVpnPermission()
    }
  )
}
