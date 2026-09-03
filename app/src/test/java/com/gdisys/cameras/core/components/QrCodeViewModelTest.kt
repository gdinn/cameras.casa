package com.gdisys.cameras.core.components

import app.cash.turbine.test
import com.gdisys.cameras.MainDispatcherRule
import com.gdisys.cameras.core.permission.domain.usecase.HasCameraPermissionUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QrCodeViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val hasCameraPermissionUseCase = mockk<HasCameraPermissionUseCase>()

  private fun createViewModel(): QrCodeViewModel {
    return QrCodeViewModel(hasCameraPermissionUseCase)
  }

  @Test
  fun `initial hasCameraPermission comes from the use case when granted`() {
    every { hasCameraPermissionUseCase() } returns true

    val viewModel = createViewModel()

    assertTrue(viewModel.hasCameraPermission.value)
  }

  @Test
  fun `initial hasCameraPermission comes from the use case when denied`() {
    every { hasCameraPermissionUseCase() } returns false

    val viewModel = createViewModel()

    assertFalse(viewModel.hasCameraPermission.value)
  }

  @Test
  fun `onPermissionResult updates the state flow`() {
    every { hasCameraPermissionUseCase() } returns false
    val viewModel = createViewModel()

    viewModel.onPermissionResult(true)

    assertTrue(viewModel.hasCameraPermission.value)
  }

  @Test
  fun `onQrCodeScanned emits the raw value once`() = runTest {
    every { hasCameraPermissionUseCase() } returns true
    val viewModel = createViewModel()

    viewModel.qrCodeScannedEvent.test {
      viewModel.onQrCodeScanned("raw-value")
      assertEquals("raw-value", awaitItem())
    }
  }

  @Test
  fun `onQrCodeScanned ignores repeated calls until resetScan is called`() = runTest {
    every { hasCameraPermissionUseCase() } returns true
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
    every { hasCameraPermissionUseCase() } returns true
    val viewModel = createViewModel()

    viewModel.uiEvent.test {
      viewModel.onCameraInitError(RuntimeException("camera boom"))
      assertEquals(ToastUiEvent.Show(QrCodeToastMessage.CAMERA_INIT_ERROR.resId), awaitItem())
    }
  }
}
