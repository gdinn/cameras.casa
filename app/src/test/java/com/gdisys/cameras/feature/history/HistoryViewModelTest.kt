package com.gdisys.cameras.feature.history

import com.gdisys.cameras.MainDispatcherRule
import com.gdisys.cameras.core.vpn.domain.VpnSessionCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class HistoryViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val vpnSessionCoordinator = mockk<VpnSessionCoordinator>()

  private val viewModel = HistoryViewModel(vpnSessionCoordinator)

  @Test
  fun `connectVpn delegates to the coordinator`() = runTest {
    coEvery { vpnSessionCoordinator.connect() } returns Result.success(Unit)

    viewModel.connectVpn()

    coVerify(exactly = 1) { vpnSessionCoordinator.connect() }
  }

  @Test
  fun `disconnectVpn delegates to the coordinator`() = runTest {
    coEvery { vpnSessionCoordinator.disconnect() } returns Result.success(Unit)

    viewModel.disconnectVpn()

    coVerify(exactly = 1) { vpnSessionCoordinator.disconnect() }
  }
}
