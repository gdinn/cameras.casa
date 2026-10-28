package com.gdisys.cameras.feature.qrcode.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun QrCodeScreen(
  scanner: QrCodeScanner,
  onQrCodeScanned: (String) -> Unit,
  onCameraInitError: (Throwable) -> Unit
) {
  // Camera permission is requested before this screen is shown, in ConfigScreen.
  scanner.Preview(
    modifier = Modifier,
    onQrCodeScanned = onQrCodeScanned,
    onCameraInitError = onCameraInitError
  )
}
