package com.gdisys.cameras.feature.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigUiStateTest {

  private fun configuredState(
    qrCodeButtonState: ConfigButtonState = ConfigButtonState.Done,
    vpnPermissionButtonState: ConfigButtonState = ConfigButtonState.Done,
    streamURLsButtonState: ConfigButtonState = ConfigButtonState.Done
  ) = ConfigUiState(
    cameraPermissionButtonState = ConfigButtonState.Done,
    qrCodeButtonState = qrCodeButtonState,
    vpnPermissionButtonState = vpnPermissionButtonState,
    streamURLsButtonState = streamURLsButtonState
  )

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

  @Test
  fun `no requirement is missing when the three button states are Done`() {
    val state = configuredState()

    assertNull(state.missingRequirement)
    assertTrue(state.canNavigateToHome)
  }

  @Test
  fun `vpn credentials are the first missing requirement`() {
    val state = configuredState(
      qrCodeButtonState = ConfigButtonState.Ready,
      vpnPermissionButtonState = ConfigButtonState.Ready,
      streamURLsButtonState = ConfigButtonState.Ready
    )

    assertEquals(ConfigRequirement.VPN_CREDENTIALS, state.missingRequirement)
    assertFalse(state.canNavigateToHome)
  }

  @Test
  fun `a qr code error also counts as missing vpn credentials`() {
    val state = configuredState(qrCodeButtonState = ConfigButtonState.Error)

    assertEquals(ConfigRequirement.VPN_CREDENTIALS, state.missingRequirement)
  }

  @Test
  fun `vpn permission is reported once credentials are valid`() {
    val state = configuredState(vpnPermissionButtonState = ConfigButtonState.Ready)

    assertEquals(ConfigRequirement.VPN_PERMISSION, state.missingRequirement)
  }

  @Test
  fun `stream urls are reported last`() {
    val state = configuredState(streamURLsButtonState = ConfigButtonState.Ready)

    assertEquals(ConfigRequirement.STREAM_URLS, state.missingRequirement)
  }

  @Test
  fun `a requirement still loading blocks the navigation`() {
    assertFalse(configuredState(streamURLsButtonState = ConfigButtonState.Loading).canNavigateToHome)
    assertEquals(
      ConfigRequirement.STREAM_URLS,
      configuredState(streamURLsButtonState = ConfigButtonState.Loading).missingRequirement
    )
  }

  @Test
  fun `the default state blocks the navigation`() {
    assertFalse(ConfigUiState().canNavigateToHome)
  }
}
