package com.gdisys.cameras.feature.config

import android.content.Intent
import app.cash.turbine.test
import com.gdisys.cameras.MainDispatcherRule
import com.gdisys.cameras.core.components.ToastUiEvent
import com.gdisys.cameras.core.permission.domain.usecase.HasCameraPermissionUseCase
import com.gdisys.cameras.core.permission.domain.usecase.HasVpnPermissionUseCase
import com.gdisys.cameras.core.storage.domain.VpnCredentialsStatus
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.model.VpnConfigDefaults
import com.gdisys.cameras.core.storage.domain.model.VpnConfigTokens
import com.gdisys.cameras.core.storage.domain.usecase.GetStreamPreferencesUseCase
import com.gdisys.cameras.core.storage.domain.usecase.GetVpnConfigStatusUseCase
import com.gdisys.cameras.core.storage.domain.usecase.ParseUserPreferencesFromQrCodeUseCase
import com.gdisys.cameras.core.storage.domain.usecase.SaveUserPreferencesUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.RequestVpnPermissionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val STREAM_URL = "http://[fd00:20::cafe]:8889/cam_160"

class ConfigViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val saveUserPreferencesUseCase = mockk<SaveUserPreferencesUseCase>()
  private val parseUserPreferencesFromQrCodeUseCase = mockk<ParseUserPreferencesFromQrCodeUseCase>()
  private val getVpnConfigStatusUseCase = mockk<GetVpnConfigStatusUseCase>()
  private val getStreamPreferencesUseCase = mockk<GetStreamPreferencesUseCase>()
  private val hasVpnPermissionUseCase = mockk<HasVpnPermissionUseCase>()
  private val hasCameraPermissionUseCase = mockk<HasCameraPermissionUseCase>()
  private val requestVpnPermissionUseCase = mockk<RequestVpnPermissionUseCase>()

  private val statusFlow = MutableStateFlow<VpnCredentialsStatus>(VpnCredentialsStatus.Loading)
  private val streamPreferencesFlow = MutableStateFlow(StreamPreferences())

  private val userPreferences = UserPreferences(
    vpnConfigDefaults = VpnConfigDefaults(
      iDns = "1.1.1.1",
      iMtu = "1420",
      pPuk = "public-key",
      pAllowedips = "0.0.0.0/0",
      pEndpoint = "vpn.example.com:51820",
      pPersistentKeepAlive = "25"
    ),
    vpnConfigTokens = VpnConfigTokens(
      iPrk = "private-key",
      iAddr = "10.0.0.2/32",
      pPsk = "pre-shared-key"
    )
  )

  private lateinit var viewModel: ConfigViewModel

  private fun createViewModel(
    hasVpnPermission: Boolean = false,
    hasCameraPermission: Boolean = false,
  ): ConfigViewModel {
    every { getVpnConfigStatusUseCase() } returns statusFlow
    every { getStreamPreferencesUseCase() } returns streamPreferencesFlow
    every { hasVpnPermissionUseCase() } returns hasVpnPermission
    every { hasCameraPermissionUseCase() } returns hasCameraPermission
    return ConfigViewModel(
      saveUserPreferencesUseCase,
      parseUserPreferencesFromQrCodeUseCase,
      getVpnConfigStatusUseCase,
      getStreamPreferencesUseCase,
      hasVpnPermissionUseCase,
      hasCameraPermissionUseCase,
      requestVpnPermissionUseCase
    )
  }

  @Before
  fun setUp() {
    viewModel = createViewModel()
  }

  @Test
  fun `uiState reflects vpn and camera permission on init`() = runTest {
    val viewModel = createViewModel(hasVpnPermission = true, hasCameraPermission = true)

    viewModel.uiState.test {
      var state = awaitItem()
      while (state.vpnPermissionButtonState != ConfigButtonState.Done ||
        state.cameraPermissionButtonState != ConfigButtonState.Done
      ) {
        state = awaitItem()
      }
      assertEquals(ConfigButtonState.Done, state.vpnPermissionButtonState)
      assertEquals(ConfigButtonState.Done, state.cameraPermissionButtonState)
    }
  }

  @Test
  fun `onShowScanner emits a navigate to scanner event when camera permission is granted`() = runTest {
    val viewModel = createViewModel(hasCameraPermission = true)

    viewModel.uiState.test {
      var state = awaitItem()
      while (state.cameraPermissionButtonState != ConfigButtonState.Done) {
        state = awaitItem()
      }

      viewModel.navigateToScannerEvent.test {
        viewModel.onShowScanner()

        awaitItem()
      }
    }
  }

  @Test
  fun `onShowScanner does not navigate when camera permission is not granted`() = runTest {
    viewModel.navigateToScannerEvent.test {
      viewModel.onShowScanner()

      expectNoEvents()
    }
  }

  @Test
  fun `onShowScanner shows a toast when camera permission is not granted`() = runTest {
    viewModel.uiEvent.test {
      viewModel.onShowScanner()

      assertEquals(
        ToastUiEvent.Show(ConfigToastMessage.CAMERA_PERMISSION_REQUIRED_FOR_QR_CODE.resId),
        awaitItem()
      )
    }
  }

  @Test
  fun `onRequestCameraPermission emits a request permission event`() = runTest {
    viewModel.requestCameraPermissionEvent.test {
      viewModel.onRequestCameraPermission()

      awaitItem()
    }
  }

  @Test
  fun `onRequestCameraPermission shows a toast and does not emit a request event when permission is already granted`() = runTest {
    val viewModel = createViewModel(hasCameraPermission = true)

    viewModel.uiState.test {
      var state = awaitItem()
      while (state.cameraPermissionButtonState != ConfigButtonState.Done) {
        state = awaitItem()
      }
    }

    viewModel.requestCameraPermissionEvent.test {
      viewModel.uiEvent.test {
        viewModel.onRequestCameraPermission()

        assertEquals(
          ToastUiEvent.Show(ConfigToastMessage.CAMERA_PERMISSION_ALREADY_GRANTED.resId),
          awaitItem()
        )
      }
      expectNoEvents()
    }
  }

  @Test
  fun `onCameraPermissionResult with granted true sets camera permission button to Done`() = runTest {
    viewModel.uiState.test {
      awaitItem()

      viewModel.onCameraPermissionResult(granted = true, shouldShowRationale = false)

      assertEquals(ConfigButtonState.Done, awaitItem().cameraPermissionButtonState)
    }
  }

  @Test
  fun `onCameraPermissionResult with granted false sets camera permission button to Ready`() = runTest {
    val viewModel = createViewModel(hasCameraPermission = true)

    viewModel.uiState.test {
      var state = awaitItem()
      while (state.cameraPermissionButtonState != ConfigButtonState.Done) {
        state = awaitItem()
      }

      viewModel.onCameraPermissionResult(granted = false, shouldShowRationale = true)

      assertEquals(ConfigButtonState.Ready, awaitItem().cameraPermissionButtonState)
    }
  }

  @Test
  fun `onCameraPermissionResult with granted false and rationale allowed shows no toast`() = runTest {
    viewModel.uiEvent.test {
      viewModel.onCameraPermissionResult(granted = false, shouldShowRationale = true)

      expectNoEvents()
    }
  }

  @Test
  fun `onCameraPermissionResult with granted false and rationale unavailable shows permanently denied toast`() = runTest {
    viewModel.uiEvent.test {
      viewModel.onCameraPermissionResult(granted = false, shouldShowRationale = false)

      assertEquals(
        ToastUiEvent.Show(ConfigToastMessage.CAMERA_PERMISSION_PERMANENTLY_DENIED.resId),
        awaitItem()
      )
    }
  }

  @Test
  fun `onVpnPermissionAccepted sets vpn permission button to Done`() = runTest {
    viewModel.uiState.test {
      val state = awaitItem()
      assertEquals(ConfigButtonState.Ready, state.vpnPermissionButtonState)

      viewModel.onVpnPermissionAccepted()

      assertEquals(ConfigButtonState.Done, awaitItem().vpnPermissionButtonState)
    }
  }

  @Test
  fun `onVpnPermissionAccepted shows a toast`() = runTest {
    viewModel.uiEvent.test {
      viewModel.onVpnPermissionAccepted()

      assertEquals(
        ToastUiEvent.Show(ConfigToastMessage.VPN_PERMISSION_ACCEPTED.resId),
        awaitItem()
      )
    }
  }

  @Test
  fun `onVpnPermissionDenied shows a toast`() = runTest {
    viewModel.uiEvent.test {
      viewModel.onVpnPermissionDenied()

      assertEquals(
        ToastUiEvent.Show(ConfigToastMessage.VPN_PERMISSION_DENIED.resId),
        awaitItem()
      )
    }
  }

  @Test
  fun `onQrCodeScanned with valid preferences saves them and shows a success toast`() = runTest {
    every { parseUserPreferencesFromQrCodeUseCase("raw-json") } returns Result.success(userPreferences)
    coEvery { saveUserPreferencesUseCase(userPreferences) } returns Result.success(Unit)

    viewModel.uiEvent.test {
      viewModel.onQrCodeScanned("raw-json")

      assertEquals(
        ToastUiEvent.Show(ConfigToastMessage.QR_CODE_LOADED_SUCCESSFULLY.resId),
        awaitItem()
      )
    }
    coVerify(exactly = 1) { saveUserPreferencesUseCase(userPreferences) }
  }

  @Test
  fun `onQrCodeScanned with null preferences resets preferences and shows invalid data toast`() = runTest {
    every { parseUserPreferencesFromQrCodeUseCase("raw-json") } returns Result.success(null)
    coEvery { saveUserPreferencesUseCase(UserPreferences()) } returns Result.success(Unit)

    viewModel.uiEvent.test {
      viewModel.onQrCodeScanned("raw-json")

      assertEquals(
        ToastUiEvent.Show(ConfigToastMessage.QR_CODE_INVALID_DATA_ERROR.resId),
        awaitItem()
      )
    }
    coVerify(exactly = 1) { saveUserPreferencesUseCase(UserPreferences()) }
  }

  @Test
  fun `onQrCodeScanned with null preferences sets qrCodeButtonState to Error`() = runTest {
    every { parseUserPreferencesFromQrCodeUseCase("raw-json") } returns Result.success(null)
    coEvery { saveUserPreferencesUseCase(UserPreferences()) } returns Result.success(Unit)

    viewModel.uiState.test {
      awaitItem()

      viewModel.onQrCodeScanned("raw-json")

      assertEquals(ConfigButtonState.Error, awaitItem().qrCodeButtonState)
    }
  }

  @Test
  fun `onQrCodeScanned with malformed json resets preferences and shows format error toast`() = runTest {
    val exception = RuntimeException("malformed")
    every { parseUserPreferencesFromQrCodeUseCase("bad-json") } returns Result.failure(exception)
    coEvery { saveUserPreferencesUseCase(UserPreferences()) } returns Result.success(Unit)

    viewModel.uiEvent.test {
      viewModel.onQrCodeScanned("bad-json")

      assertEquals(
        ToastUiEvent.Show(ConfigToastMessage.QR_CODE_FORMAT_ERROR.resId),
        awaitItem()
      )
    }
    coVerify(exactly = 1) { saveUserPreferencesUseCase(UserPreferences()) }
  }

  @Test
  fun `onQrCodeScanned with malformed json sets qrCodeButtonState to Error`() = runTest {
    val exception = RuntimeException("malformed")
    every { parseUserPreferencesFromQrCodeUseCase("bad-json") } returns Result.failure(exception)
    coEvery { saveUserPreferencesUseCase(UserPreferences()) } returns Result.success(Unit)

    viewModel.uiState.test {
      awaitItem()

      viewModel.onQrCodeScanned("bad-json")

      assertEquals(ConfigButtonState.Error, awaitItem().qrCodeButtonState)
    }
  }

  @Test
  fun `onQrCodeScanned clears a previous Error on a new successful scan`() = runTest {
    statusFlow.value = VpnCredentialsStatus.Loaded(hasValidCredentials = false)
    every { parseUserPreferencesFromQrCodeUseCase("bad-json") } returns Result.failure(RuntimeException("malformed"))
    every { parseUserPreferencesFromQrCodeUseCase("raw-json") } returns Result.success(userPreferences)
    coEvery { saveUserPreferencesUseCase(UserPreferences()) } returns Result.success(Unit)
    coEvery { saveUserPreferencesUseCase(userPreferences) } returns Result.success(Unit)

    viewModel.uiState.test {
      assertEquals(ConfigButtonState.Ready, awaitItem().qrCodeButtonState)

      viewModel.onQrCodeScanned("bad-json")
      assertEquals(ConfigButtonState.Error, awaitItem().qrCodeButtonState)

      viewModel.onQrCodeScanned("raw-json")
      assertEquals(ConfigButtonState.Ready, awaitItem().qrCodeButtonState)
    }
  }

  @Test
  fun `onQrCodeScanned shows a toast when saving fails`() = runTest {
    every { parseUserPreferencesFromQrCodeUseCase("raw-json") } returns Result.success(userPreferences)
    coEvery { saveUserPreferencesUseCase(userPreferences) } returns Result.failure(RuntimeException("boom"))

    viewModel.uiEvent.test {
      viewModel.onQrCodeScanned("raw-json")

      assertEquals(
        ToastUiEvent.Show(ConfigToastMessage.SAVE_PREFERENCES_ERROR.resId),
        awaitItem()
      )
    }
  }

  @Test
  fun `onQrCodeScanned sets qrCodeButtonState to Error when saving fails`() = runTest {
    every { parseUserPreferencesFromQrCodeUseCase("raw-json") } returns Result.success(userPreferences)
    coEvery { saveUserPreferencesUseCase(userPreferences) } returns Result.failure(RuntimeException("boom"))

    viewModel.uiState.test {
      awaitItem()

      viewModel.onQrCodeScanned("raw-json")

      assertEquals(ConfigButtonState.Error, awaitItem().qrCodeButtonState)
    }
  }

  @Test
  fun `acceptVpnPermission shows a toast when permission is already granted`() = runTest {
    every { requestVpnPermissionUseCase() } returns null

    viewModel.uiEvent.test {
      viewModel.acceptVpnPermission()

      assertEquals(
        ToastUiEvent.Show(ConfigToastMessage.PERMISSION_ALREADY_GRANTED.resId),
        awaitItem()
      )
    }
  }

  @Test
  fun `acceptVpnPermission emits a request permission event when consent is still needed`() = runTest {
    every { requestVpnPermissionUseCase() } returns mockk<Intent>()

    viewModel.vpnPermissionUiEvent.test {
      viewModel.acceptVpnPermission()

      // The event carries no payload: the consent Intent is the route's to build and launch.
      assertEquals(VpnPermissionUiEvent.RequestPermission, awaitItem())
    }
  }

  @Test
  fun `streamURLsButtonState is Ready when no url is stored`() = runTest {
    viewModel.uiState.test {
      assertEquals(ConfigButtonState.Ready, awaitItem().streamURLsButtonState)
    }
  }

  @Test
  fun `streamURLsButtonState is Done when at least one url is stored`() = runTest {
    streamPreferencesFlow.value = StreamPreferences(streamUrls = listOf(STREAM_URL))
    val viewModel = createViewModel()

    viewModel.uiState.test {
      assertEquals(ConfigButtonState.Done, awaitItem().streamURLsButtonState)
    }
  }

  @Test
  fun `onNavigateToHomeRequested navigates when every requirement is satisfied`() = runTest {
    statusFlow.value = VpnCredentialsStatus.Loaded(hasValidCredentials = true)
    streamPreferencesFlow.value = StreamPreferences(streamUrls = listOf(STREAM_URL))
    val viewModel = createViewModel(hasVpnPermission = true)

    viewModel.navigateToHomeEvent.test {
      viewModel.onNavigateToHomeRequested()

      awaitItem()
    }
  }

  @Test
  fun `onNavigateToHomeRequested is blocked by missing vpn credentials`() = runTest {
    statusFlow.value = VpnCredentialsStatus.Loaded(hasValidCredentials = false)
    streamPreferencesFlow.value = StreamPreferences(streamUrls = listOf(STREAM_URL))
    val viewModel = createViewModel(hasVpnPermission = true)

    viewModel.navigateToHomeEvent.test {
      viewModel.uiEvent.test {
        viewModel.onNavigateToHomeRequested()

        assertEquals(
          ToastUiEvent.Show(ConfigToastMessage.VPN_CREDENTIALS_MISSING.resId),
          awaitItem()
        )
      }
      expectNoEvents()
    }
  }

  @Test
  fun `onNavigateToHomeRequested is blocked by the missing vpn permission`() = runTest {
    statusFlow.value = VpnCredentialsStatus.Loaded(hasValidCredentials = true)
    streamPreferencesFlow.value = StreamPreferences(streamUrls = listOf(STREAM_URL))
    val viewModel = createViewModel(hasVpnPermission = false)

    viewModel.navigateToHomeEvent.test {
      viewModel.uiEvent.test {
        viewModel.onNavigateToHomeRequested()

        assertEquals(
          ToastUiEvent.Show(ConfigToastMessage.VPN_PERMISSION_MISSING.resId),
          awaitItem()
        )
      }
      expectNoEvents()
    }
  }

  @Test
  fun `onNavigateToHomeRequested is blocked by missing stream urls`() = runTest {
    statusFlow.value = VpnCredentialsStatus.Loaded(hasValidCredentials = true)
    streamPreferencesFlow.value = StreamPreferences()
    val viewModel = createViewModel(hasVpnPermission = true)

    viewModel.navigateToHomeEvent.test {
      viewModel.uiEvent.test {
        viewModel.onNavigateToHomeRequested()

        assertEquals(
          ToastUiEvent.Show(ConfigToastMessage.STREAM_URLS_MISSING.resId),
          awaitItem()
        )
      }
      expectNoEvents()
    }
  }

  @Test
  fun `onNavigateToHomeRequested reports the first missing requirement`() = runTest {
    statusFlow.value = VpnCredentialsStatus.Loaded(hasValidCredentials = false)
    streamPreferencesFlow.value = StreamPreferences()
    val viewModel = createViewModel(hasVpnPermission = false)

    viewModel.uiEvent.test {
      viewModel.onNavigateToHomeRequested()

      assertEquals(
        ToastUiEvent.Show(ConfigToastMessage.VPN_CREDENTIALS_MISSING.resId),
        awaitItem()
      )
    }
  }

  @Test
  fun `onBackPressedBlocked reports the first missing requirement`() = runTest {
    statusFlow.value = VpnCredentialsStatus.Loaded(hasValidCredentials = true)
    streamPreferencesFlow.value = StreamPreferences()
    val viewModel = createViewModel(hasVpnPermission = true)

    viewModel.uiEvent.test {
      viewModel.onBackPressedBlocked()

      assertEquals(
        ToastUiEvent.Show(ConfigToastMessage.STREAM_URLS_MISSING.resId),
        awaitItem()
      )
    }
  }

  @Test
  fun `onBackPressedBlocked points to the navigate button when nothing is missing`() = runTest {
    statusFlow.value = VpnCredentialsStatus.Loaded(hasValidCredentials = true)
    streamPreferencesFlow.value = StreamPreferences(streamUrls = listOf(STREAM_URL))
    val viewModel = createViewModel(hasVpnPermission = true)

    viewModel.uiEvent.test {
      viewModel.onBackPressedBlocked()

      assertEquals(
        ToastUiEvent.Show(ConfigToastMessage.NAVIGATE_TO_HOME_REQUIRED.resId),
        awaitItem()
      )
    }
  }

  @Test
  fun `refreshCameraPermissionState sets camera permission button to Done when permission is granted`() = runTest {
    every { hasCameraPermissionUseCase() } returns true

    viewModel.uiState.test {
      awaitItem()

      viewModel.refreshCameraPermissionState()

      assertEquals(ConfigButtonState.Done, awaitItem().cameraPermissionButtonState)
    }
  }

  @Test
  fun `refreshCameraPermissionState sets camera permission button to Ready when permission is not granted`() = runTest {
    val viewModel = createViewModel(hasCameraPermission = true)

    viewModel.uiState.test {
      var state = awaitItem()
      while (state.cameraPermissionButtonState != ConfigButtonState.Done) {
        state = awaitItem()
      }

      every { hasCameraPermissionUseCase() } returns false
      viewModel.refreshCameraPermissionState()

      assertEquals(ConfigButtonState.Ready, awaitItem().cameraPermissionButtonState)
    }
  }
}
