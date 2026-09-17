package com.gdisys.cameras.core.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.gdisys.cameras.core.DEBUG_TAG

@Composable
fun QrCodeRoute(
  onCodeScanned: (String) -> Unit,
  viewModel: QrCodeViewModel = hiltViewModel()
) {
  // A QrCodeViewModel é retida entre exibições do scanner, então é preciso
  // reabilitar a leitura de QR code sempre que a tela é reaberta.
  LaunchedEffect(Unit) {
    viewModel.resetScan()
  }

  LaunchedEffect(viewModel) {
    viewModel.qrCodeScannedEvent.collect { rawValue ->
      Log.d(DEBUG_TAG, "QrCodeRoute: $rawValue")
      onCodeScanned(rawValue)
    }
  }

  ToastDisplayer(toastUiEvent = viewModel.uiEvent)

  QrCodeScreen(
    onQrCodeScanned = viewModel::onQrCodeScanned,
    onCameraInitError = viewModel::onCameraInitError
  )
}
