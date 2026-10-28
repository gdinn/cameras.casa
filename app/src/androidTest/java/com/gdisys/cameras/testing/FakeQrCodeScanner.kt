package com.gdisys.cameras.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.gdisys.cameras.feature.qrcode.components.QrCodeScanner

/**
 * Stands in for the camera, for tests that provide it through `LocalQrCodeScanner`. As soon as it
 * is composed it reports [error] if set, or [rawValue] otherwise, as a camera would on its first
 * frame.
 */
class FakeQrCodeScanner(
  private val rawValue: String = "",
  private val error: Throwable? = null
) : QrCodeScanner {

  @Composable
  override fun Preview(
    modifier: Modifier,
    onQrCodeScanned: (String) -> Unit,
    onCameraInitError: (Throwable) -> Unit
  ) {
    LaunchedEffect(Unit) {
      if (error != null) onCameraInitError(error) else onQrCodeScanned(rawValue)
    }
    Box(modifier)
  }
}
