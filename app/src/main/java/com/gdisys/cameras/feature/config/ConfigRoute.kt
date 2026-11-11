package com.gdisys.cameras.feature.config

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

  // Camera permission can be granted externally, from system settings, while this screen is in
  // the background.
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    viewModel.refreshCameraPermissionState()
    viewModel.refreshNotificationPermissionState()
  }

  LaunchedEffect(canNavigateBackToHome) {
    viewModel.setCanNavigateBackToHome(canNavigateBackToHome)
  }

  // Back only leaves this screen when there is a Home in the back stack *and* the configuration
  // is complete; otherwise it is consumed here and turned into a toast.
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

  // The ViewModel only emits this event when consent is still needed, and the event carries the
  // system consent Intent to launch.
  LaunchActivityResultOnEvent(
    events = viewModel.vpnPermissionUiEvent
      .filterIsInstance<VpnPermissionUiEvent.RequestPermission>()
      .map { it.intent },
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

  // The ViewModel only asks on Android 13+, where the permission exists.
  @SuppressLint("InlinedApi")
  val notificationPermission = Manifest.permission.POST_NOTIFICATIONS
  LaunchActivityResultOnEvent(
    events = viewModel.requestNotificationPermissionEvent.map { notificationPermission },
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    viewModel.onNotificationPermissionResult(granted)
  }

  ToastDisplayer(
    toastUiEvent = viewModel.uiEvent
  )

  ConfigScreen(
    uiState = uiState,
    onShowScanner = viewModel::onShowScanner,
    acceptVpnPermission = viewModel::acceptVpnPermission,
    onRequestCameraPermission = viewModel::onRequestCameraPermission,
    onRequestNotificationPermission = viewModel::onRequestNotificationPermission,
    onNavigateToStreamURLs = onNavigateToStreamURLs,
    onNavigateToHome = viewModel::onNavigateToHomeRequested
  )
}
