package com.gdisys.cameras.feature.cameras

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.gdisys.cameras.MainDispatcherRule
import com.gdisys.cameras.core.components.ToastUiEvent
import com.gdisys.cameras.core.storage.domain.usecase.GetVpnConfigUseCase
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import com.gdisys.cameras.core.vpn.domain.usecase.ConnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.DisconnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.ObserveVpnStateUseCase
import com.gdisys.cameras.core.webrtc.data.WhepConnectionManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.webrtc.VideoSink

class HomeViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val vpnState = MutableStateFlow(VpnTunnelState.DISCONNECTED)

  private val observeVpnStateUseCase = mockk<ObserveVpnStateUseCase>()
  private val connectVpnUseCase = mockk<ConnectVpnUseCase>()
  private val disconnectVpnUseCase = mockk<DisconnectVpnUseCase>()
  private val getVpnConfigUseCase = mockk<GetVpnConfigUseCase>()
  private val whepConnectionManager = mockk<WhepConnectionManager>(relaxed = true)

  private lateinit var viewModel: HomeViewModel

  private val defaultStreams = listOf(
    "http://[fd00:20::cafe]:8889/cam_160",
    "http://[fd00:20::cafe]:8889/cam_161",
    "http://[fd00:20::cafe]:8889/cam_162",
    "http://[fd00:20::cafe]:8889/cam_163"
  )

  private val validConfig = VpnConfig(
    privateKey = "private-key",
    address = "10.0.0.2/32",
    dns = "1.1.1.1",
    publicKey = "public-key",
    preSharedKey = "pre-shared-key",
    endpoint = "vpn.example.com:51820",
    allowedIps = "0.0.0.0/0",
    keepAlive = "25",
    mtu = "1420"
  )

  @Before
  fun setUp() {
    every { observeVpnStateUseCase() } returns vpnState
    viewModel = HomeViewModel(
      observeVpnStateUseCase,
      connectVpnUseCase,
      disconnectVpnUseCase,
      getVpnConfigUseCase,
      whepConnectionManager
    )
  }

  @Test
  fun `uiState is Loading while vpn is not connected`() = runTest {
    assertEquals(HomeUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `uiState becomes Ready with the default streams once vpn connects`() = runTest {
    viewModel.uiState.test {
      assertEquals(HomeUiState.Loading, awaitItem())

      vpnState.value = VpnTunnelState.CONNECTED
      assertEquals(HomeUiState.Ready(streams = defaultStreams, focusedStream = null), awaitItem())

      vpnState.value = VpnTunnelState.DISCONNECTED
      assertEquals(HomeUiState.Loading, awaitItem())
    }
  }

  @Test
  fun `focusStream and clearFocusedStream update the focused stream while connected`() = runTest {
    viewModel.uiState.test {
      awaitItem()
      vpnState.value = VpnTunnelState.CONNECTED
      awaitItem()

      viewModel.focusStream(defaultStreams[1])
      val focused = awaitItem() as HomeUiState.Ready
      assertEquals(defaultStreams[1], focused.focusedStream)

      viewModel.clearFocusedStream()
      val cleared = awaitItem() as HomeUiState.Ready
      assertNull(cleared.focusedStream)
    }
  }

  @Test
  fun `moveStreamUp swaps the stream with the previous one`() = runTest {
    viewModel.uiState.test {
      awaitItem()
      vpnState.value = VpnTunnelState.CONNECTED
      awaitItem()

      viewModel.moveStreamUp(2)
      val moved = awaitItem() as HomeUiState.Ready
      assertEquals(
        listOf(defaultStreams[0], defaultStreams[2], defaultStreams[1], defaultStreams[3]),
        moved.streams
      )
    }
  }

  @Test
  fun `moveStreamUp at index 0 does not change the order`() = runTest {
    viewModel.uiState.test {
      awaitItem()
      vpnState.value = VpnTunnelState.CONNECTED
      awaitItem()

      viewModel.moveStreamUp(0)
      expectNoEvents()
    }
  }

  @Test
  fun `moveStreamDown swaps the stream with the next one`() = runTest {
    viewModel.uiState.test {
      awaitItem()
      vpnState.value = VpnTunnelState.CONNECTED
      awaitItem()

      viewModel.moveStreamDown(0)
      val moved = awaitItem() as HomeUiState.Ready
      assertEquals(
        listOf(defaultStreams[1], defaultStreams[0], defaultStreams[2], defaultStreams[3]),
        moved.streams
      )
    }
  }

  @Test
  fun `moveStreamDown at the last index does not change the order`() = runTest {
    viewModel.uiState.test {
      awaitItem()
      vpnState.value = VpnTunnelState.CONNECTED
      awaitItem()

      viewModel.moveStreamDown(defaultStreams.lastIndex)
      expectNoEvents()
    }
  }

  @Test
  fun `connectStream delegates to WhepConnectionManager`() {
    val videoSink = mockk<VideoSink>()
    every { whepConnectionManager.connect(any(), any(), any()) } just Runs

    viewModel.connectStream("stream-url", videoSink)

    verify(exactly = 1) { whepConnectionManager.connect("stream-url", videoSink, any()) }
  }

  @Test
  fun `connectStream shows a toast when the connection fails`() = runTest {
    val videoSink = mockk<VideoSink>()
    val onErrorSlot = slot<(Throwable) -> Unit>()
    every { whepConnectionManager.connect(any(), any(), capture(onErrorSlot)) } just Runs

    viewModel.uiEvent.test {
      viewModel.connectStream("stream-url", videoSink)
      onErrorSlot.captured(RuntimeException("boom"))

      assertEquals(
        ToastUiEvent.Show(HomeToastMessage.STREAM_CONNECTION_ERROR.resId),
        awaitItem()
      )
    }
  }

  @Test
  fun `disconnectStream delegates to WhepConnectionManager`() {
    viewModel.disconnectStream("stream-url")

    verify(exactly = 1) { whepConnectionManager.disconnect("stream-url") }
  }

  @Test
  fun `connectVpn connects successfully without toast or navigation`() = runTest {
    coEvery { getVpnConfigUseCase() } returns Result.success(validConfig)
    coEvery { connectVpnUseCase(validConfig) } returns Result.success(Unit)

    turbineScope {
      val toastTurbine = viewModel.uiEvent.testIn(this)
      val navigateTurbine = viewModel.navigateUiEvent.testIn(this)

      viewModel.connectVpn()

      coVerify(exactly = 1) { connectVpnUseCase(validConfig) }
      toastTurbine.expectNoEvents()
      navigateTurbine.expectNoEvents()

      toastTurbine.cancelAndIgnoreRemainingEvents()
      navigateTurbine.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `connectVpn shows a toast and navigates to config when getVpnConfigUseCase fails`() = runTest {
    coEvery { getVpnConfigUseCase() } returns Result.failure(RuntimeException("boom"))

    turbineScope {
      val toastTurbine = viewModel.uiEvent.testIn(this)
      val navigateTurbine = viewModel.navigateUiEvent.testIn(this)

      viewModel.connectVpn()

      assertEquals(
        ToastUiEvent.Show(HomeToastMessage.VPN_CONNECTION_ERROR.resId),
        toastTurbine.awaitItem()
      )
      assertEquals(HomeNavigateUiEvent.ToConfig, navigateTurbine.awaitItem())

      toastTurbine.cancelAndIgnoreRemainingEvents()
      navigateTurbine.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `connectVpn shows a toast and navigates to config when connectVpnUseCase fails`() = runTest {
    coEvery { getVpnConfigUseCase() } returns Result.success(validConfig)
    coEvery { connectVpnUseCase(validConfig) } returns Result.failure(RuntimeException("boom"))

    turbineScope {
      val toastTurbine = viewModel.uiEvent.testIn(this)
      val navigateTurbine = viewModel.navigateUiEvent.testIn(this)

      viewModel.connectVpn()

      assertEquals(
        ToastUiEvent.Show(HomeToastMessage.VPN_CONNECTION_ERROR.resId),
        toastTurbine.awaitItem()
      )
      assertEquals(HomeNavigateUiEvent.ToConfig, navigateTurbine.awaitItem())

      toastTurbine.cancelAndIgnoreRemainingEvents()
      navigateTurbine.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `disconnectVpn delegates to the use case`() = runTest {
    coEvery { disconnectVpnUseCase() } returns Result.success(Unit)

    viewModel.disconnectVpn()

    coVerify(exactly = 1) { disconnectVpnUseCase() }
  }

  @Test
  fun `onCleared closes all WhepConnectionManager connections`() {
    val viewModelStore = ViewModelStore()
    viewModelStore.put("home", viewModel)

    viewModelStore.clear()

    verify(exactly = 1) { whepConnectionManager.closeAll() }
  }
}
