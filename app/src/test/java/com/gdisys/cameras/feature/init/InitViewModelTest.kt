package com.gdisys.cameras.feature.init

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.gdisys.cameras.MainDispatcherRule
import com.gdisys.cameras.core.components.ToastUiEvent
import com.gdisys.cameras.core.storage.domain.VpnCredentialsStatus
import com.gdisys.cameras.core.storage.domain.usecase.GetVpnConfigStatusUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InitViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val getVpnConfigStatusUseCase = mockk<GetVpnConfigStatusUseCase>()

  @Test
  fun `Loading status does not navigate or show a toast`() = runTest {
    every { getVpnConfigStatusUseCase() } returns flowOf(VpnCredentialsStatus.Loading)
    val viewModel = InitViewModel(getVpnConfigStatusUseCase)

    turbineScope {
      val navigateTurbine = viewModel.navigateUiEvent.testIn(this)
      val toastTurbine = viewModel.uiEvent.testIn(this)

      navigateTurbine.expectNoEvents()
      toastTurbine.expectNoEvents()

      navigateTurbine.cancelAndIgnoreRemainingEvents()
      toastTurbine.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `Loaded true navigates to home`() = runTest {
    every { getVpnConfigStatusUseCase() } returns flowOf(VpnCredentialsStatus.Loaded(hasValidCredentials = true))
    val viewModel = InitViewModel(getVpnConfigStatusUseCase)

    viewModel.navigateUiEvent.test {
      assertEquals(NavigateUiEvent.ToHome, awaitItem())
    }
  }

  @Test
  fun `Loaded false shows a toast and navigates to config`() = runTest {
    every { getVpnConfigStatusUseCase() } returns flowOf(VpnCredentialsStatus.Loaded(hasValidCredentials = false))
    val viewModel = InitViewModel(getVpnConfigStatusUseCase)

    turbineScope {
      val toastTurbine = viewModel.uiEvent.testIn(this)
      val navigateTurbine = viewModel.navigateUiEvent.testIn(this)

      assertEquals(
        ToastUiEvent.Show(InitToastMessage.CREDENTIALS_NOT_FOUND.resId),
        toastTurbine.awaitItem()
      )
      assertEquals(NavigateUiEvent.ToConfig, navigateTurbine.awaitItem())

      toastTurbine.cancelAndIgnoreRemainingEvents()
      navigateTurbine.cancelAndIgnoreRemainingEvents()
    }
  }
}
