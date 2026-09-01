package com.gdisys.cameras.core.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun QrCodeRoute(
  onCodeScanned: (String) -> Unit,
  viewModel: QrCodeViewModel = hiltViewModel()
) {
  val hasCameraPermission by viewModel.hasCameraPermission.collectAsState()

  // Launcher para solicitar a permissão
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = viewModel::onPermissionResult
  )

  // Solicita a permissão assim que a tela abre, caso ainda não tenha
  LaunchedEffect(Unit) {
    if (!hasCameraPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  LaunchedEffect(viewModel) {
    viewModel.qrCodeScannedEvent.collect { rawValue ->
      onCodeScanned(rawValue)
    }
  }

  ToastDisplayer(toastUiEvent = viewModel.uiEvent)

  QrCodeScreen(
    hasCameraPermission = hasCameraPermission,
    onRequestCameraPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
    onQrCodeScanned = viewModel::onQrCodeScanned,
    onCameraInitError = viewModel::onCameraInitError
  )
}
