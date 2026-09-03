package com.gdisys.cameras.feature.config

import android.content.Intent
import app.cash.turbine.test
import com.gdisys.cameras.MainDispatcherRule
import com.gdisys.cameras.core.components.ToastUiEvent
import com.gdisys.cameras.core.storage.domain.VpnCredentialsStatus
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.model.VpnConfigDefaults
import com.gdisys.cameras.core.storage.domain.model.VpnConfigTokens
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

class ConfigViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val saveUserPreferencesUseCase = mockk<SaveUserPreferencesUseCase>()
  private val parseUserPreferencesFromQrCodeUseCase = mockk<ParseUserPreferencesFromQrCodeUseCase>()
  private val getVpnConfigStatusUseCase = mockk<GetVpnConfigStatusUseCase>()
  private val requestVpnPermissionUseCase = mockk<RequestVpnPermissionUseCase>()

  private val statusFlow = MutableStateFlow<VpnCredentialsStatus>(VpnCredentialsStatus.Loading)

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

  @Before
  fun setUp() {
    every { getVpnConfigStatusUseCase() } returns statusFlow
    viewModel = ConfigViewModel(
      saveUserPreferencesUseCase,
      parseUserPreferencesFromQrCodeUseCase,
      getVpnConfigStatusUseCase,
      requestVpnPermissionUseCase
    )
  }

  @Test
  fun `uiState reflects Loading, ConfigurationLoaded, NeedsConfiguration and Scanning`() = runTest {
    viewModel.uiState.test {
      assertEquals(ConfigUiState.Loading, awaitItem())

      statusFlow.value = VpnCredentialsStatus.Loaded(hasValidCredentials = true)
      assertEquals(ConfigUiState.ConfigurationLoaded, awaitItem())

      statusFlow.value = VpnCredentialsStatus.Loaded(hasValidCredentials = false)
      assertEquals(ConfigUiState.NeedsConfiguration, awaitItem())

      viewModel.onShowScanner()
      assertEquals(ConfigUiState.Scanning, awaitItem())

      statusFlow.value = VpnCredentialsStatus.Loading
      assertEquals(ConfigUiState.Loading, awaitItem())
    }
  }

  @Test
  fun `onQrCodeScanned with valid preferences saves them without showing a toast`() = runTest {
    every { parseUserPreferencesFromQrCodeUseCase("raw-json") } returns Result.success(userPreferences)
    coEvery { saveUserPreferencesUseCase(userPreferences) } returns Result.success(Unit)

    viewModel.uiEvent.test {
      viewModel.onQrCodeScanned("raw-json")
      expectNoEvents()
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
  fun `updateUserPreferences shows a toast when saving fails`() = runTest {
    coEvery { saveUserPreferencesUseCase(userPreferences) } returns Result.failure(RuntimeException("boom"))

    viewModel.uiEvent.test {
      viewModel.updateUserPreferences(userPreferences)

      assertEquals(
        ToastUiEvent.Show(ConfigToastMessage.SAVE_PREFERENCES_ERROR.resId),
        awaitItem()
      )
    }
  }

  @Test
  fun `updateUserPreferences does not show a toast on success`() = runTest {
    coEvery { saveUserPreferencesUseCase(userPreferences) } returns Result.success(Unit)

    viewModel.uiEvent.test {
      viewModel.updateUserPreferences(userPreferences)
      expectNoEvents()
    }
    coVerify(exactly = 1) { saveUserPreferencesUseCase(userPreferences) }
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
  fun `acceptVpnPermission emits a request permission event when an intent is returned`() = runTest {
    val intent = mockk<Intent>()
    every { requestVpnPermissionUseCase() } returns intent

    viewModel.vpnPermissionUiEvent.test {
      viewModel.acceptVpnPermission()

      assertEquals(VpnPermissionUiEvent.RequestPermission(intent), awaitItem())
    }
  }
}
