package com.gdisys.cameras.feature.qrcode

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.components.ToastDisplayer
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
