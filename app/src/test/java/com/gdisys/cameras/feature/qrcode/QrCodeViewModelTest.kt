package com.gdisys.cameras.feature.qrcode

import app.cash.turbine.test
import com.gdisys.cameras.MainDispatcherRule
import com.gdisys.cameras.core.components.ToastUiEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QrCodeViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private fun createViewModel(): QrCodeViewModel {
    return QrCodeViewModel()
  }

  @Test
  fun `onQrCodeScanned emits the raw value once`() = runTest {
    val viewModel = createViewModel()

    viewModel.qrCodeScannedEvent.test {
      viewModel.onQrCodeScanned("raw-value")
      assertEquals("raw-value", awaitItem())
    }
  }

  @Test
  fun `onQrCodeScanned ignores repeated calls until resetScan is called`() = runTest {
    val viewModel = createViewModel()

    viewModel.qrCodeScannedEvent.test {
      viewModel.onQrCodeScanned("first")
      assertEquals("first", awaitItem())

      viewModel.onQrCodeScanned("second")
      expectNoEvents()

      viewModel.resetScan()
      viewModel.onQrCodeScanned("third")
      assertEquals("third", awaitItem())
    }
  }

  @Test
  fun `onCameraInitError shows a toast`() = runTest {
    val viewModel = createViewModel()

    viewModel.uiEvent.test {
      viewModel.onCameraInitError(RuntimeException("camera boom"))
      assertEquals(ToastUiEvent.Show(QrCodeToastMessage.CAMERA_INIT_ERROR.resId), awaitItem())
    }
  }

  @Test
  fun `onCameraInitError emits a navigate back event`() = runTest {
    val viewModel = createViewModel()

    viewModel.navigateBackEvent.test {
      viewModel.onCameraInitError(RuntimeException("camera boom"))
      awaitItem()
    }
  }

  @Test
  fun `onBackPressed shows a toast`() = runTest {
    val viewModel = createViewModel()

    viewModel.uiEvent.test {
      viewModel.onBackPressed()
      assertEquals(ToastUiEvent.Show(QrCodeToastMessage.READING_CANCELLED.resId), awaitItem())
    }
  }

  @Test
  fun `onBackPressed emits a navigate back event`() = runTest {
    val viewModel = createViewModel()

    viewModel.navigateBackEvent.test {
      viewModel.onBackPressed()
      awaitItem()
    }
  }
}
