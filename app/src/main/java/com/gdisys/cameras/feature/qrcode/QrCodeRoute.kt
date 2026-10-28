package com.gdisys.cameras.feature.qrcode

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.gdisys.cameras.core.components.ToastDisplayer
import com.gdisys.cameras.feature.qrcode.components.LocalQrCodeScanner
import com.gdisys.cameras.feature.qrcode.components.QrCodeScreen

@Composable
fun QrCodeRoute(
  onCodeScanned: (String) -> Unit,
  onNavigateBack: () -> Unit,
  viewModel: QrCodeViewModel = hiltViewModel()
) {
  // The QrCodeViewModel survives between scanner visits, so scanning has to be re-enabled every
  // time the screen is reopened.
  LaunchedEffect(Unit) {
    viewModel.resetScan()
  }

  BackHandler {
    viewModel.onBackPressed()
  }

  LaunchedEffect(viewModel) {
    // The raw value is never logged: a real QR code carries the WireGuard private key and the
    // preshared key, and logcat is readable by anyone with adb access and ends up in test artifacts.
    viewModel.qrCodeScannedEvent.collect { rawValue ->
      onCodeScanned(rawValue)
    }
  }

  LaunchedEffect(viewModel) {
    viewModel.navigateBackEvent.collect {
      onNavigateBack()
    }
  }

  ToastDisplayer(toastUiEvent = viewModel.uiEvent)

  QrCodeScreen(
    scanner = LocalQrCodeScanner.current,
    onQrCodeScanned = viewModel::onQrCodeScanned,
    onCameraInitError = viewModel::onCameraInitError
  )
}
