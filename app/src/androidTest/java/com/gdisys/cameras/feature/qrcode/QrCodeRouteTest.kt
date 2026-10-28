package com.gdisys.cameras.feature.qrcode

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gdisys.cameras.core.components.LocalToastPresenter
import com.gdisys.cameras.feature.qrcode.components.LocalQrCodeScanner
import com.gdisys.cameras.feature.qrcode.components.QrCodeScanner
import com.gdisys.cameras.testing.FakeQrCodeScanner
import com.gdisys.cameras.testing.RecordingToastPresenter
import com.gdisys.cameras.testing.setThemedContent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val RAW_QR_CODE = """{"fake":"qr"}"""

@RunWith(AndroidJUnit4::class)
class QrCodeRouteTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val scannedValues = CopyOnWriteArrayList<String>()
  private val navigateBackCount = AtomicInteger()
  private val toasts = RecordingToastPresenter()

  @Test
  fun scannedValueReachesOnCodeScanned() {
    setQrCodeRoute(FakeQrCodeScanner(rawValue = RAW_QR_CODE))

    composeRule.waitUntil { scannedValues.isNotEmpty() }
    assertEquals(listOf(RAW_QR_CODE), scannedValues.toList())
    assertEquals(0, navigateBackCount.get())
  }

  @Test
  fun cameraInitErrorToastsAndNavigatesBack() {
    setQrCodeRoute(FakeQrCodeScanner(error = IllegalStateException("no camera")))

    composeRule.waitUntil { navigateBackCount.get() == 1 }
    assertEquals(listOf(QrCodeToastMessage.CAMERA_INIT_ERROR.resId), toasts.shown.toList())
    assertEquals(emptyList<String>(), scannedValues.toList())
  }

  // The ViewModel injects nothing, so it is built by hand instead of through Hilt.
  private fun setQrCodeRoute(scanner: QrCodeScanner) {
    composeRule.setThemedContent {
      CompositionLocalProvider(
        LocalQrCodeScanner provides scanner,
        LocalToastPresenter provides toasts
      ) {
        QrCodeRoute(
          onCodeScanned = { scannedValues += it },
          onNavigateBack = { navigateBackCount.incrementAndGet() },
          viewModel = QrCodeViewModel()
        )
      }
    }
  }
}
