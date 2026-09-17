package com.gdisys.cameras.feature.config

import android.Manifest
import android.app.Activity.RESULT_OK
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdisys.cameras.core.components.LaunchActivityResultOnEvent
import com.gdisys.cameras.core.components.ToastDisplayer
import com.gdisys.cameras.feature.config.components.ConfigScreen
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

@Composable
fun ConfigRoute(
  viewModel: ConfigViewModel = hiltViewModel(),
  qrCodeRawJsonResult: String?,
  onQrCodeResultConsumed: () -> Unit,
  onNavigateToScanner: () -> Unit,
  onNavigateToHome: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(qrCodeRawJsonResult) {
    if (qrCodeRawJsonResult != null) {
      viewModel.onQrCodeScanned(qrCodeRawJsonResult)
      onQrCodeResultConsumed()
    }
  }

  LaunchedEffect(Unit) {
    viewModel.navigateToScannerEvent.collect {
      onNavigateToScanner()
    }
  }

  LaunchActivityResultOnEvent(
    events = viewModel.vpnPermissionUiEvent
      .filterIsInstance<VpnPermissionUiEvent.RequestPermission>()
      .map { it.intent },
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == RESULT_OK) {
      viewModel.onVpnPermissionAccepted()
    }
  }

  LaunchActivityResultOnEvent(
    events = viewModel.requestCameraPermissionEvent.map { Manifest.permission.CAMERA },
    contract = ActivityResultContracts.RequestPermission(),
    onResult = viewModel::onCameraPermissionResult
  )

  ToastDisplayer(
    toastUiEvent = viewModel.uiEvent
  )

  ConfigScreen(
    uiState = uiState,
    onShowScanner = viewModel::onShowScanner,
    acceptVpnPermission = viewModel::acceptVpnPermission,
    onRequestCameraPermission = viewModel::onRequestCameraPermission,
    onNavigateToHome = onNavigateToHome
  )
}
