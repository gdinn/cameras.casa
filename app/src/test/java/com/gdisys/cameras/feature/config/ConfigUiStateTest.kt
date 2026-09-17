package com.gdisys.cameras.feature.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigUiStateTest {

  @Test
  fun `isQrCodeButtonEnabled is true when camera permission button state is Done`() {
    val state = ConfigUiState(cameraPermissionButtonState = ConfigButtonState.Done)

    assertTrue(state.isQrCodeButtonEnabled)
  }

  @Test
  fun `isQrCodeButtonEnabled is false when camera permission button state is not Done`() {
    assertFalse(ConfigUiState(cameraPermissionButtonState = ConfigButtonState.Ready).isQrCodeButtonEnabled)
    assertFalse(ConfigUiState(cameraPermissionButtonState = ConfigButtonState.Loading).isQrCodeButtonEnabled)
    assertFalse(ConfigUiState(cameraPermissionButtonState = ConfigButtonState.Error).isQrCodeButtonEnabled)
  }
}
