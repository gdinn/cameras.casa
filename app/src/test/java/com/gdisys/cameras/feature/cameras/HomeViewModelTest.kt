package com.gdisys.cameras.feature.cameras

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.gdisys.cameras.MainDispatcherRule
import com.gdisys.cameras.core.components.ToastUiEvent
import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import com.gdisys.cameras.core.storage.domain.usecase.GetStreamPreferencesUseCase
import com.gdisys.cameras.core.storage.domain.usecase.GetVpnConfigUseCase
import com.gdisys.cameras.core.storage.domain.usecase.UpdateStreamOrderUseCase
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import com.gdisys.cameras.core.vpn.domain.usecase.ConnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.DisconnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.ObserveVpnStateUseCase
import com.gdisys.cameras.core.webrtc.StreamConnectionRepository
import com.gdisys.cameras.feature.cameras.logic.movedToPage
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
  private val getStreamPreferencesUseCase = mockk<GetStreamPreferencesUseCase>()
  private val updateStreamOrderUseCase = mockk<UpdateStreamOrderUseCase>()
  private val connectVpnUseCase = mockk<ConnectVpnUseCase>()
  private val disconnectVpnUseCase = mockk<DisconnectVpnUseCase>()
  private val getVpnConfigUseCase = mockk<GetVpnConfigUseCase>()
  private val streamConnectionRepository = mockk<StreamConnectionRepository>(relaxed = true)

  private lateinit var viewModel: HomeViewModel

  private val defaultStreams = listOf(
    "http://[fd00:20::cafe]:8889/cam_160",
    "http://[fd00:20::cafe]:8889/cam_161",
    "http://[fd00:20::cafe]:8889/cam_162",
    "http://[fd00:20::cafe]:8889/cam_163"
  )

  private val portraitGrid = GridPreferences(columns = 1, rows = 1, dynamicRows = true)
  private val landscapeGrid = GridPreferences(columns = 2, rows = 1, dynamicRows = true)

  /** The two orders differ on purpose: that is what proves orientation picks the right list. */
  private val streamPreferences = MutableStateFlow(
    StreamPreferences(
      streamUrls = defaultStreams,
      portraitOrder = defaultStreams,
      landscapeOrder = defaultStreams.reversed(),
      portraitGrid = portraitGrid,
      landscapeGrid = landscapeGrid
    )
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
    every { getStreamPreferencesUseCase() } returns streamPreferences
    coEvery { updateStreamOrderUseCase(any(), any()) } returns Result.success(Unit)
    viewModel = HomeViewModel(
      observeVpnStateUseCase,
      getStreamPreferencesUseCase,
      updateStreamOrderUseCase,
      connectVpnUseCase,
      disconnectVpnUseCase,
      getVpnConfigUseCase,
      streamConnectionRepository
    )
  }

  private fun readyState(
    streams: List<String> = defaultStreams,
    focusedStream: String? = null,
    grid: GridPreferences = portraitGrid,
    orientation: StreamOrientation = StreamOrientation.PORTRAIT
  ) = HomeUiState.Ready(
    streams = streams,
    focusedStream = focusedStream,
    grid = grid,
    orientation = orientation
  )

  @Test
  fun `uiState is Loading while vpn is not connected`() = runTest {
    assertEquals(HomeUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `uiState becomes Ready with the stored streams once vpn connects`() = runTest {
    viewModel.uiState.test {
      assertEquals(HomeUiState.Loading, awaitItem())

      vpnState.value = VpnTunnelState.CONNECTED
      assertEquals(readyState(), awaitItem())

      vpnState.value = VpnTunnelState.DISCONNECTED
      assertEquals(HomeUiState.Loading, awaitItem())
    }
  }

  @Test
  fun `uiState is Empty when there is no stream url configured`() = runTest {
    streamPreferences.value = StreamPreferences()

    viewModel.uiState.test {
      assertEquals(HomeUiState.Empty, awaitItem())

      vpnState.value = VpnTunnelState.CONNECTED
      expectNoEvents()
    }
  }

  @Test
  fun `uiState follows a dynamic number of stream urls`() = runTest {
    vpnState.value = VpnTunnelState.CONNECTED

    viewModel.uiState.test {
      assertEquals(readyState(), awaitItem())

      listOf(2, 7).forEach { count ->
        val urls = List(count) { "http://[fd00:20::cafe]:8889/cam_$it" }
        streamPreferences.value = StreamPreferences(
          streamUrls = urls,
          portraitOrder = urls,
          landscapeOrder = urls,
          portraitGrid = portraitGrid,
          landscapeGrid = landscapeGrid
        )

        assertEquals(readyState(streams = urls), awaitItem())
      }
    }
  }

  @Test
  fun `changing the orientation swaps the order and the grid`() = runTest {
    vpnState.value = VpnTunnelState.CONNECTED

    viewModel.uiState.test {
      assertEquals(readyState(), awaitItem())

      viewModel.onOrientationChanged(StreamOrientation.LANDSCAPE)
      assertEquals(
        readyState(
          streams = defaultStreams.reversed(),
          grid = landscapeGrid,
          orientation = StreamOrientation.LANDSCAPE
        ),
        awaitItem()
      )

      viewModel.onOrientationChanged(StreamOrientation.PORTRAIT)
      assertEquals(readyState(), awaitItem())
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
  fun `onStreamsReordered persists only the current orientation`() = runTest {
    val newOrder = defaultStreams.reversed()

    viewModel.onStreamsReordered(newOrder)

    coVerify(exactly = 1) {
      updateStreamOrderUseCase(StreamOrientation.PORTRAIT, newOrder)
    }
    coVerify(exactly = 0) {
      updateStreamOrderUseCase(StreamOrientation.LANDSCAPE, any())
    }
  }

  @Test
  fun `onStreamsReordered persists against the orientation in effect`() = runTest {
    val newOrder = defaultStreams.reversed()

    viewModel.onOrientationChanged(StreamOrientation.LANDSCAPE)
    viewModel.onStreamsReordered(newOrder)

    coVerify(exactly = 1) {
      updateStreamOrderUseCase(StreamOrientation.LANDSCAPE, newOrder)
    }
    coVerify(exactly = 0) {
      updateStreamOrderUseCase(StreamOrientation.PORTRAIT, any())
    }
  }

  @Test
  fun `a drag across pages persists the whole order of the current orientation`() = runTest {
    // Fixed grid of 2 items per page: the first stream is dragged to the right edge and lands at
    // the start of the next page. What gets persisted is the complete order, not just the page's.
    val newOrder = movedToPage(
      order = defaultStreams,
      url = defaultStreams[0],
      page = 1,
      itemsPerPage = 2,
      atStart = true
    )
    assertEquals(
      listOf(defaultStreams[1], defaultStreams[2], defaultStreams[0], defaultStreams[3]),
      newOrder
    )

    viewModel.onStreamsReordered(newOrder)

    coVerify(exactly = 1) { updateStreamOrderUseCase(StreamOrientation.PORTRAIT, newOrder) }
  }

  @Test
  fun `onStreamsReordered shows a toast when persisting fails`() = runTest {
    coEvery { updateStreamOrderUseCase(any(), any()) } returns
      Result.failure(RuntimeException("boom"))

    viewModel.uiEvent.test {
      viewModel.onStreamsReordered(defaultStreams.reversed())

      assertEquals(
        ToastUiEvent.Show(HomeToastMessage.STREAM_ORDER_SAVE_ERROR.resId),
        awaitItem()
      )
    }
  }

  @Test
  fun `connectStream delegates to WhepConnectionManager`() {
    val videoSink = mockk<VideoSink>()
    every { streamConnectionRepository.connect(any(), any(), any()) } just Runs

    viewModel.connectStream("stream-url", videoSink)

    verify(exactly = 1) { streamConnectionRepository.connect("stream-url", videoSink, any()) }
  }

  @Test
  fun `connectStream shows a toast when the connection fails`() = runTest {
    val videoSink = mockk<VideoSink>()
    val onErrorSlot = slot<(Throwable) -> Unit>()
    every { streamConnectionRepository.connect(any(), any(), capture(onErrorSlot)) } just Runs

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

    verify(exactly = 1) { streamConnectionRepository.disconnect("stream-url") }
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

    verify(exactly = 1) { streamConnectionRepository.closeAll() }
  }
}
