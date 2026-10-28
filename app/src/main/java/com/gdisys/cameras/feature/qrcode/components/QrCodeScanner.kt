package com.gdisys.cameras.feature.qrcode.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Renders a camera preview and reports the QR payloads it reads.
 *
 * Production uses [CameraXQrCodeScanner]. Instrumented tests provide a fake through
 * [LocalQrCodeScanner], because the emulator camera cannot be pointed at a QR code.
 */
interface QrCodeScanner {
  @Composable
  fun Preview(
    modifier: Modifier,
    onQrCodeScanned: (String) -> Unit,
    onCameraInitError: (Throwable) -> Unit
  )
}

/**
 * The [QrCodeScanner] for the composition. Read by `QrCodeRoute`, which passes it to
 * `QrCodeScreen` as a parameter, the same split as `LocalEglBase`.
 */
val LocalQrCodeScanner = staticCompositionLocalOf<QrCodeScanner> { CameraXQrCodeScanner }
