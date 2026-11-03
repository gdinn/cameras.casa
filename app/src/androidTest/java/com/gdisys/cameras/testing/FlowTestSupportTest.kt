package com.gdisys.cameras.testing

import com.gdisys.cameras.app.navigation.NavigationRoute
import com.gdisys.cameras.core.permission.domain.CameraPermissionRepository
import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.core.storage.domain.model.isValid
import com.gdisys.cameras.core.storage.domain.usecase.ParseUserPreferencesFromQrCodeUseCase
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Inject

private const val CAM_1 = "http://10.0.0.1:8889/cam_1/whep"
private const val CAM_2 = "http://10.0.0.1:8889/cam_2/whep"

/** The helpers of [FlowTest] do what the scenarios will rely on, before any scenario uses them. */
@HiltAndroidTest
class FlowTestSupportTest : FlowTest() {

  @Inject lateinit var cameraPermissionRepository: CameraPermissionRepository

  @Test
  fun onlyTheFirstFixtureHoldsValidCredentials() {
    val parse = ParseUserPreferencesFromQrCodeUseCase()

    assertNotNull(parse(QrFixture.VALID.read()).getOrThrow())
    QrFixture.entries.filter { it != QrFixture.VALID }.forEach { fixture ->
      assertTrue("$fixture was accepted", parse(fixture.read()).getOrNull() == null)
    }
  }

  @Test
  fun seededStateIsPersisted() {
    val fixedGrid = GridPreferences(columns = 2, rows = 2, dynamicRows = false)

    seed.credentials()
    seed.streams(listOf(CAM_1, CAM_2), portraitGrid = fixedGrid)

    assertTrue(storedUserPreferences().vpnConfigTokens?.isValid() == true)
    val streamPreferences = storedStreamPreferences()
    assertEquals(listOf(CAM_1, CAM_2), streamPreferences.streamUrls)
    assertEquals(listOf(CAM_1, CAM_2), streamPreferences.landscapeOrder)
    assertEquals(fixedGrid, streamPreferences.portraitGrid)
  }

  @Test
  fun seededCredentialsAndStreamsOpenHome() {
    seed.credentials()
    seed.streams(listOf(CAM_1))

    launchApp()

    assertEquals(listOf(NavigationRoute.Home), backStackRoutes())
  }

  @Test
  fun anInjectedQrReachesConfig() {
    launchApp()

    injectQr(QrFixture.VALID.read())

    assertTrue(storedUserPreferences().vpnConfigTokens?.isValid() == true)
  }

  @Test
  fun theCameraPermissionStartsRevokedAndCanBeGranted() {
    assertFalse(cameraPermissionRepository.hasCameraPermission())

    DeviceState.grantCameraPermission()

    assertTrue(cameraPermissionRepository.hasCameraPermission())
  }
}
