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
import com.gdisys.cameras.core.storage.domain.usecase.ObserveIsAdminUseCase
import com.gdisys.cameras.core.storage.domain.usecase.UpdateStreamOrderUseCase
import com.gdisys.cameras.core.vpn.domain.TunnelCoordinator
import com.gdisys.cameras.core.vpn.domain.TunnelHolder
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
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
import kotlinx.coroutines.flow.MutableSharedFlow
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
  private val observeIsAdminUseCase = mockk<ObserveIsAdminUseCase>()
  private val isAdmin = MutableStateFlow(false)
  private val updateStreamOrderUseCase = mockk<UpdateStreamOrderUseCase>()
  private val tunnelCoordinator = FakeTunnelCoordinator()
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

  @Before
  fun setUp() {
    every { observeVpnStateUseCase() } returns vpnState
    every { getStreamPreferencesUseCase() } returns streamPreferences
    every { observeIsAdminUseCase() } returns isAdmin
    coEvery { updateStreamOrderUseCase(any(), any()) } returns Result.success(Unit)
    viewModel = HomeViewModel(
      observeVpnStateUseCase,
      getStreamPreferencesUseCase,
      observeIsAdminUseCase,
      updateStreamOrderUseCase,
      tunnelCoordinator,
      streamConnectionRepository
    )
  }

  private fun readyState(
    streams: List<String> = defaultStreams,
    focusedStream: String? = null,
    grid: GridPreferences = portraitGrid,
    orientation: StreamOrientation = StreamOrientation.PORTRAIT,
    failedStreams: Set<String> = emptySet()
  ) = HomeUiState.Ready(
    streams = streams,
    focusedStream = focusedStream,
    grid = grid,
    orientation = orientation,
    failedStreams = failedStreams
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
  fun `an admin's focused stream offers the history of its camera`() = runTest {
    isAdmin.value = true
    vpnState.value = VpnTunnelState.CONNECTED

    viewModel.focusStream(defaultStreams[1])

    assertEquals(
      readyState(focusedStream = defaultStreams[1]).copy(historyCamera = "cam_161"),
      viewModel.uiState.value
    )
  }

  @Test
  fun `no history is offered without a focused stream`() = runTest {
    isAdmin.value = true
    vpnState.value = VpnTunnelState.CONNECTED

    assertEquals(readyState(), viewModel.uiState.value)
  }

  @Test
  fun `no history is offered to a regular user`() = runTest {
    vpnState.value = VpnTunnelState.CONNECTED

    viewModel.focusStream(defaultStreams[1])

    assertEquals(readyState(focusedStream = defaultStreams[1]), viewModel.uiState.value)
  }

  @Test
  fun `no history is offered for a stream URL that names no camera`() = runTest {
    val garage = "http://[fd00:20::cafe]:8889/garage"
    streamPreferences.value = streamPreferences.value.copy(
      streamUrls = listOf(garage),
      portraitOrder = listOf(garage)
    )
    isAdmin.value = true
    vpnState.value = VpnTunnelState.CONNECTED

    viewModel.focusStream(garage)

    assertEquals(null, (viewModel.uiState.value as HomeUiState.Ready).historyCamera)
  }

  @Test
  fun `the history button follows the admin status`() = runTest {
    vpnState.value = VpnTunnelState.CONNECTED
    viewModel.focusStream(defaultStreams[0])

    isAdmin.value = true
    assertEquals("cam_160", (viewModel.uiState.value as HomeUiState.Ready).historyCamera)

    isAdmin.value = false
    assertEquals(null, (viewModel.uiState.value as HomeUiState.Ready).historyCamera)
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

  /** Connects [url] and returns the error callback the repository received. */
  private fun connectCapturingOnError(url: String): (Throwable) -> Unit {
    val onErrorSlot = slot<(Throwable) -> Unit>()
    every { streamConnectionRepository.connect(url, any(), capture(onErrorSlot)) } just Runs
    viewModel.connectStream(url, mockk())
    return onErrorSlot.captured
  }

  @Test
  fun `a failed stream connection marks only that stream as failed`() = runTest {
    vpnState.value = VpnTunnelState.CONNECTED

    viewModel.uiState.test {
      assertEquals(readyState(), awaitItem())

      connectCapturingOnError(defaultStreams[1])(RuntimeException("boom"))
      assertEquals(readyState(failedStreams = setOf(defaultStreams[1])), awaitItem())
    }
  }

  @Test
  fun `connecting a failed stream again clears the failure`() = runTest {
    vpnState.value = VpnTunnelState.CONNECTED

    viewModel.uiState.test {
      awaitItem()
      connectCapturingOnError(defaultStreams[0])(RuntimeException("boom"))
      assertEquals(readyState(failedStreams = setOf(defaultStreams[0])), awaitItem())

      viewModel.connectStream(defaultStreams[0], mockk())
      assertEquals(readyState(), awaitItem())
    }
  }

  @Test
  fun `disconnecting a failed stream clears the failure`() = runTest {
    vpnState.value = VpnTunnelState.CONNECTED

    viewModel.uiState.test {
      awaitItem()
      connectCapturingOnError(defaultStreams[0])(RuntimeException("boom"))
      awaitItem()

      viewModel.disconnectStream(defaultStreams[0])
      assertEquals(readyState(), awaitItem())
    }
  }

  @Test
  fun `a failed stream removed from the list is not reported as failed`() = runTest {
    vpnState.value = VpnTunnelState.CONNECTED
    val remaining = defaultStreams.drop(1)

    viewModel.uiState.test {
      awaitItem()
      connectCapturingOnError(defaultStreams[0])(RuntimeException("boom"))
      awaitItem()

      streamPreferences.value = streamPreferences.value.copy(
        streamUrls = remaining,
        portraitOrder = remaining,
        landscapeOrder = remaining
      )
      assertEquals(readyState(streams = remaining), awaitItem())
    }
  }

  @Test
  fun `disconnectStream delegates to WhepConnectionManager`() {
    viewModel.disconnectStream("stream-url")

    verify(exactly = 1) { streamConnectionRepository.disconnect("stream-url") }
  }

  @Test
  fun `onResumed with streams asks for the tunnel`() = runTest {
    viewModel.onResumed()

    assertEquals(listOf("request"), tunnelCoordinator.calls)
  }

  @Test
  fun `onPaused gives the tunnel back`() = runTest {
    viewModel.onResumed()
    viewModel.onPaused()

    assertEquals(listOf("request", "release"), tunnelCoordinator.calls)
  }

  @Test
  fun `onResumed without stream urls does not ask for the tunnel`() = runTest {
    streamPreferences.value = StreamPreferences()

    viewModel.onResumed()

    assertEquals(emptyList<String>(), tunnelCoordinator.calls.filter { it == "request" })
  }

  @Test
  fun `the first stream url asks for the tunnel on a resumed Home`() = runTest {
    streamPreferences.value = StreamPreferences()
    viewModel.onResumed()

    streamPreferences.value = StreamPreferences(streamUrls = defaultStreams)

    assertEquals("request", tunnelCoordinator.calls.last())
  }

  @Test
  fun `removing the last stream url gives the tunnel back`() = runTest {
    viewModel.onResumed()

    streamPreferences.value = StreamPreferences()

    assertEquals(listOf("request", "release"), tunnelCoordinator.calls)
  }

  @Test
  fun `a connect failure while resumed shows a toast and navigates to config`() = runTest {
    turbineScope {
      val toastTurbine = viewModel.uiEvent.testIn(this)
      val navigateTurbine = viewModel.navigateUiEvent.testIn(this)
      viewModel.onResumed()

      tunnelCoordinator.failures.emit(RuntimeException("boom"))

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
  fun `a connect failure while paused is left to the screen on display`() = runTest {
    turbineScope {
      val toastTurbine = viewModel.uiEvent.testIn(this)
      val navigateTurbine = viewModel.navigateUiEvent.testIn(this)
      viewModel.onResumed()
      viewModel.onPaused()

      tunnelCoordinator.failures.emit(RuntimeException("boom"))

      toastTurbine.expectNoEvents()
      navigateTurbine.expectNoEvents()

      toastTurbine.cancelAndIgnoreRemainingEvents()
      navigateTurbine.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `clearing the view model gives the tunnel back`() = runTest {
    val viewModelStore = ViewModelStore()
    viewModelStore.put("home", viewModel)
    viewModel.onResumed()

    viewModelStore.clear()

    assertEquals("release", tunnelCoordinator.calls.last())
  }

  @Test
  fun `onCleared closes all WhepConnectionManager connections`() {
    val viewModelStore = ViewModelStore()
    viewModelStore.put("home", viewModel)

    viewModelStore.clear()

    verify(exactly = 1) { streamConnectionRepository.closeAll() }
  }

  /** Records what Home asks of the coordinator, always for [HOME_TUNNEL_HOLDER]. */
  private class FakeTunnelCoordinator : TunnelCoordinator {
    val calls = mutableListOf<String>()
    val failures = MutableSharedFlow<Throwable>()
    override val connectFailures = failures

    override fun request(holder: TunnelHolder) {
      assertEquals(HOME_TUNNEL_HOLDER, holder)
      calls += "request"
    }

    override fun release(holder: TunnelHolder) {
      assertEquals(HOME_TUNNEL_HOLDER, holder)
      calls += "release"
    }

    override fun holdIfUp(holder: TunnelHolder) = throw UnsupportedOperationException()

    override fun holds(holder: TunnelHolder) = throw UnsupportedOperationException()

    override fun clear() = throw UnsupportedOperationException()
  }
}
