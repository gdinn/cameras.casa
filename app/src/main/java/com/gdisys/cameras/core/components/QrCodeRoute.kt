package com.gdisys.cameras.core.components

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.gdisys.cameras.core.DEBUG_TAG

@Composable
fun QrCodeRoute(
  onCodeScanned: (String) -> Unit,
  onNavigateBack: () -> Unit,
  viewModel: QrCodeViewModel = hiltViewModel()
) {
  // A QrCodeViewModel é retida entre exibições do scanner, então é preciso
  // reabilitar a leitura de QR code sempre que a tela é reaberta.
  LaunchedEffect(Unit) {
    viewModel.resetScan()
  }

  BackHandler {
    viewModel.onBackPressed()
  }

  LaunchedEffect(viewModel) {
    viewModel.qrCodeScannedEvent.collect { rawValue ->
      Log.d(DEBUG_TAG, "QrCodeRoute: $rawValue")
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
    onQrCodeScanned = viewModel::onQrCodeScanned,
    onCameraInitError = viewModel::onCameraInitError
  )
}
