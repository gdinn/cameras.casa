package com.gdisys.cameras.feature.config

import android.Manifest
import android.app.Activity.RESULT_OK
import android.net.VpnService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdisys.cameras.core.components.LaunchActivityResultOnEvent
import com.gdisys.cameras.core.components.ToastDisplayer
import com.gdisys.cameras.feature.config.components.ConfigScreen
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

@Composable
fun ConfigRoute(
  viewModel: ConfigViewModel = hiltViewModel(),
  qrCodeRawJsonResult: String?,
  canNavigateBackToHome: Boolean,
  onQrCodeResultConsumed: () -> Unit,
  onNavigateToScanner: () -> Unit,
  onNavigateToStreamURLs: () -> Unit,
  onNavigateToHome: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val activity = LocalActivity.current
  val context = LocalContext.current

  // A permissão de câmera pode ser concedida externamente, pelas configurações
  // do sistema, enquanto esta tela está em segundo plano.
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    viewModel.refreshCameraPermissionState()
  }

  LaunchedEffect(canNavigateBackToHome) {
    viewModel.setCanNavigateBackToHome(canNavigateBackToHome)
  }

  // O back só sai desta tela quando há Home na pilha de navegação *e* a configuração está
  // completa; caso contrário ele é consumido aqui e vira toast.
  BackHandler(enabled = !uiState.canNavigateBackToHome || !uiState.canNavigateToHome) {
    viewModel.onBackPressedBlocked()
  }

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

  LaunchedEffect(Unit) {
    viewModel.navigateToHomeEvent.collect {
      onNavigateToHome()
    }
  }

  // The consent Intent is built here, not in the ViewModel. `VpnService.prepare` returns null when
  // consent has already been given, in which case there is nothing to launch — `mapNotNull` drops
  // the event and the ViewModel's own check has already toasted PERMISSION_ALREADY_GRANTED.
  LaunchActivityResultOnEvent(
    events = viewModel.vpnPermissionUiEvent
      .filterIsInstance<VpnPermissionUiEvent.RequestPermission>()
      .mapNotNull { VpnService.prepare(context) },
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == RESULT_OK) {
      viewModel.onVpnPermissionAccepted()
    } else {
      viewModel.onVpnPermissionDenied()
    }
  }

  LaunchActivityResultOnEvent(
    events = viewModel.requestCameraPermissionEvent.map { Manifest.permission.CAMERA },
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    val shouldShowRationale = activity != null &&
      ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    viewModel.onCameraPermissionResult(granted, shouldShowRationale)
  }

  ToastDisplayer(
    toastUiEvent = viewModel.uiEvent
  )

  ConfigScreen(
    uiState = uiState,
    onShowScanner = viewModel::onShowScanner,
    acceptVpnPermission = viewModel::acceptVpnPermission,
    onRequestCameraPermission = viewModel::onRequestCameraPermission,
    onNavigateToStreamURLs = onNavigateToStreamURLs,
    onNavigateToHome = viewModel::onNavigateToHomeRequested
  )
}
