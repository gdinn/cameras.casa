package com.gdisys.cameras.feature.history.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.gdisys.cameras.MainDispatcherRule
import com.gdisys.cameras.core.components.ToastUiEvent
import com.gdisys.cameras.core.recordings.PlaybackStatus
import com.gdisys.cameras.core.recordings.RecordingPlayer
import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.recordings.domain.usecase.GetNeighborRecordingUseCase
import com.gdisys.cameras.core.recordings.domain.usecase.RecordingDirection
import com.gdisys.cameras.core.vpn.domain.TunnelCoordinator
import com.gdisys.cameras.core.vpn.domain.TunnelHolder
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.gdisys.cameras.core.vpn.domain.usecase.ObserveVpnStateUseCase
import com.gdisys.cameras.feature.history.HistoryError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime

private const val CAM = "cam_10"

private fun recording(day: Int, hour: Int, minute: Int): Recording {
  val name = "cam_10_2026-11-${"%02d".format(day)}_${"%02d".format(hour)}-${"%02d".format(minute)}-00.mp4"
  return Recording(CAM, LocalDateTime.of(2026, 11, day, hour, minute), name)
}

private val AT_1510 = recording(10, 15, 10)
private val AT_1500 = recording(10, 15, 0)
private val AT_1515 = recording(10, 15, 15)
private val NEXT_DAY = recording(11, 0, 0)

class RecordingPlayerViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val vpnState = MutableStateFlow(VpnTunnelState.DISCONNECTED)
  private val observeVpnStateUseCase = mockk<ObserveVpnStateUseCase>()
  private val getNeighborRecordingUseCase = mockk<GetNeighborRecordingUseCase>()
  private val tunnelCoordinator = FakeTunnelCoordinator()
  private val player = FakeRecordingPlayer()
  private val savedStateHandle = SavedStateHandle(
    mapOf(
      PLAYER_CAMERA_ARG to CAM,
      PLAYER_FILE_NAME_ARG to AT_1510.fileName,
      PLAYER_NEAREST_NOTICE_ARG to true
    )
  )

  private lateinit var viewModel: RecordingPlayerViewModel

  @Before
  fun setUp() {
    every { observeVpnStateUseCase() } returns vpnState
    coEvery { getNeighborRecordingUseCase(AT_1510, RecordingDirection.PREVIOUS) } returns Result.success(AT_1500)
    coEvery { getNeighborRecordingUseCase(AT_1510, RecordingDirection.NEXT) } returns Result.success(AT_1515)
    viewModel = newViewModel()
  }

  private fun newViewModel(handle: SavedStateHandle = savedStateHandle) = RecordingPlayerViewModel(
    handle,
    observeVpnStateUseCase,
    getNeighborRecordingUseCase,
    tunnelCoordinator,
    player
  )

  private val state get() = viewModel.uiState.value

  /** On screen with the tunnel up: the player starts. */
  private fun open() {
    viewModel.onResumed()
    vpnState.value = VpnTunnelState.CONNECTED
  }

  // arguments

  @Test
  fun `the recording and the notice come from the route`() {
    assertEquals(RecordingPlayerUiState(AT_1510, nearestNotice = true), state)
  }

  @Test
  fun `the notice is off unless the route says otherwise`() {
    val handle = SavedStateHandle(mapOf(PLAYER_CAMERA_ARG to CAM, PLAYER_FILE_NAME_ARG to AT_1510.fileName))

    assertFalse(newViewModel(handle).uiState.value.nearestNotice)
  }

  @Test
  fun `a file that is not a recording of the camera is a programming error`() {
    val handle = SavedStateHandle(mapOf(PLAYER_CAMERA_ARG to CAM, PLAYER_FILE_NAME_ARG to "notes.txt"))

    assertThrows(IllegalStateException::class.java) { newViewModel(handle) }
  }

  @Test
  fun `missing arguments are a programming error`() {
    assertThrows(IllegalStateException::class.java) { newViewModel(SavedStateHandle()) }
    assertThrows(IllegalStateException::class.java) { newViewModel(SavedStateHandle(mapOf(PLAYER_CAMERA_ARG to CAM))) }
  }

  // lifecycle

  @Test
  fun `the player waits for the screen and the tunnel`() {
    vpnState.value = VpnTunnelState.CONNECTED
    assertEquals(emptyList<String>(), player.calls)

    viewModel.onResumed()

    assertEquals(listOf("play ${AT_1510.fileName} at 0"), player.calls)
    assertEquals(listOf("request"), tunnelCoordinator.calls)
  }

  @Test
  fun `the player waits for the tunnel after resuming`() {
    viewModel.onResumed()
    assertEquals(emptyList<String>(), player.calls)

    vpnState.value = VpnTunnelState.CONNECTED

    assertEquals(listOf("play ${AT_1510.fileName} at 0"), player.calls)
  }

  @Test
  fun `the player status shows on screen`() {
    open()

    player.status.value = PlaybackStatus.Ready
    assertEquals(PlayerStatus.Playing, state.status)

    player.status.value = PlaybackStatus.Loading
    assertEquals(PlayerStatus.Loading, state.status)
  }

  @Test
  fun `pausing stops the player and resuming plays again from the same position`() {
    open()
    player.positionMillis = 42_000

    viewModel.onPaused()
    assertEquals(listOf("request", "release"), tunnelCoordinator.calls)
    assertEquals("stop", player.calls.last())

    viewModel.onResumed()
    assertEquals("play ${AT_1510.fileName} at 42000", player.calls.last())
  }

  @Test
  fun `the tunnel coming back does not start the player twice`() {
    open()

    vpnState.value = VpnTunnelState.DISCONNECTED
    vpnState.value = VpnTunnelState.CONNECTED

    assertEquals(1, player.calls.count { it.startsWith("play") })
  }

  @Test
  fun `the player and the tunnel are released when the screen is gone`() {
    val store = ViewModelStore()
    store.put("player", viewModel)

    store.clear()

    assertEquals("release", player.calls.last())
    assertEquals(listOf("release"), tunnelCoordinator.calls)
  }

  // failures

  @Test
  fun `a read failure stops the player, shows its message and keeps the position`() {
    open()
    player.positionMillis = 30_000

    player.status.value = PlaybackStatus.Failed(RecordingsException.Unreachable(IOException()))

    assertEquals(PlayerStatus.Failed(HistoryError.UNREACHABLE), state.status)
    assertEquals("stop", player.calls.last())

    viewModel.onRetry()
    assertEquals("play ${AT_1510.fileName} at 30000", player.calls.last())
    assertEquals(PlayerStatus.Loading, state.status)
  }

  @Test
  fun `a player error that is not the share's is a plain failure`() {
    open()

    player.status.value = PlaybackStatus.Failed(IllegalStateException("decoder"))

    assertEquals(PlayerStatus.Failed(HistoryError.FAILED), state.status)
  }

  @Test
  fun `retry does nothing unless something failed`() {
    open()

    viewModel.onRetry()

    assertEquals(1, player.calls.count { it.startsWith("play") })
  }

  @Test
  fun `a failed tunnel while waiting is reported, and retry asks for the tunnel again`() = runTest {
    viewModel.onResumed()

    tunnelCoordinator.failures.emit(IllegalStateException("handshake"))
    assertEquals(PlayerStatus.Failed(HistoryError.VPN_CONNECTION), state.status)

    viewModel.onRetry()
    assertEquals(listOf("request", "request"), tunnelCoordinator.calls)
    vpnState.value = VpnTunnelState.CONNECTED
    assertEquals(listOf("play ${AT_1510.fileName} at 0"), player.calls)
  }

  @Test
  fun `a failed tunnel is ignored once playing or while paused`() = runTest {
    tunnelCoordinator.failures.emit(IllegalStateException("handshake"))
    assertEquals(PlayerStatus.Loading, state.status)

    open()
    tunnelCoordinator.failures.emit(IllegalStateException("handshake"))
    assertEquals(PlayerStatus.Loading, state.status)
  }

  // neighbors

  @Test
  fun `next plays the following recording from its start, without the notice`() {
    open()
    player.positionMillis = 10_000

    viewModel.onNext()

    assertEquals(RecordingPlayerUiState(AT_1515, nearestNotice = false), state)
    assertEquals(listOf("play ${AT_1510.fileName} at 0", "stop", "play ${AT_1515.fileName} at 0"), player.calls)
    assertEquals(AT_1515.fileName, savedStateHandle.get<String>(PLAYER_FILE_NAME_ARG))
  }

  @Test
  fun `previous plays the recording before`() {
    open()

    viewModel.onPrevious()

    assertEquals(AT_1500, state.recording)
    assertEquals("play ${AT_1500.fileName} at 0", player.calls.last())
  }

  @Test
  fun `the buttons are busy while a neighbor is looked up, and a second press is ignored`() {
    val pending = CompletableDeferred<Result<Recording?>>()
    coEvery { getNeighborRecordingUseCase(AT_1510, RecordingDirection.NEXT) } coAnswers { pending.await() }
    open()

    viewModel.onNext()
    viewModel.onPrevious()
    assertEquals(RecordingDirection.NEXT, state.looking)

    pending.complete(Result.success(AT_1515))
    assertEquals(null, state.looking)
    coVerify(exactly = 0) { getNeighborRecordingUseCase(any(), RecordingDirection.PREVIOUS) }
  }

  @Test
  fun `at either end a toast says there is nothing more and playback goes on`() = runTest {
    coEvery { getNeighborRecordingUseCase(AT_1510, RecordingDirection.PREVIOUS) } returns Result.success(null)
    coEvery { getNeighborRecordingUseCase(AT_1510, RecordingDirection.NEXT) } returns Result.success(null)
    open()
    player.status.value = PlaybackStatus.Ready

    viewModel.uiEvent.test {
      viewModel.onPrevious()
      assertEquals(ToastUiEvent.Show(RecordingPlayerToastMessage.NO_EARLIER_RECORDING.resId), awaitItem())
      viewModel.onNext()
      assertEquals(ToastUiEvent.Show(RecordingPlayerToastMessage.NO_LATER_RECORDING.resId), awaitItem())
    }
    assertEquals(AT_1510, state.recording)
    assertEquals(PlayerStatus.Playing, state.status)
    assertEquals(1, player.calls.count { it.startsWith("play") })
  }

  @Test
  fun `a failed lookup is told and playback goes on`() = runTest {
    coEvery { getNeighborRecordingUseCase(AT_1510, RecordingDirection.NEXT) } returns
      Result.failure(RecordingsException.Unreachable(IOException()))
    open()

    viewModel.uiEvent.test {
      viewModel.onNext()
      assertEquals(ToastUiEvent.Show(RecordingPlayerToastMessage.NEIGHBOR_LOOKUP_FAILED.resId), awaitItem())
    }
    assertEquals(AT_1510, state.recording)
  }

  @Test
  fun `the end of a file advances to the next one, across midnight`() {
    coEvery { getNeighborRecordingUseCase(AT_1510, RecordingDirection.NEXT) } returns Result.success(NEXT_DAY)
    open()

    player.status.value = PlaybackStatus.Ended

    assertEquals(NEXT_DAY, state.recording)
    assertEquals("play ${NEXT_DAY.fileName} at 0", player.calls.last())
  }

  @Test
  fun `the end of the newest file stops on the ended state`() {
    coEvery { getNeighborRecordingUseCase(AT_1510, RecordingDirection.NEXT) } returns Result.success(null)
    open()

    player.status.value = PlaybackStatus.Ended

    assertEquals(PlayerStatus.Ended, state.status)
    assertEquals("stop", player.calls.last())
  }

  @Test
  fun `a failed lookup at the end of a file stops on the ended state`() {
    coEvery { getNeighborRecordingUseCase(AT_1510, RecordingDirection.NEXT) } returns
      Result.failure(RecordingsException.Unreachable(IOException()))
    open()

    player.status.value = PlaybackStatus.Ended

    assertEquals(PlayerStatus.Ended, state.status)
  }

  @Test
  fun `previous from the ended state plays again`() {
    coEvery { getNeighborRecordingUseCase(AT_1510, RecordingDirection.NEXT) } returns Result.success(null)
    open()
    player.status.value = PlaybackStatus.Ended

    viewModel.onPrevious()

    assertEquals(AT_1500, state.recording)
    assertEquals(PlayerStatus.Loading, state.status)
    assertEquals("play ${AT_1500.fileName} at 0", player.calls.last())
  }

  @Test
  fun `a neighbor found while paused plays on resume`() {
    val pending = CompletableDeferred<Result<Recording?>>()
    coEvery { getNeighborRecordingUseCase(AT_1510, RecordingDirection.NEXT) } coAnswers { pending.await() }
    open()
    viewModel.onNext()
    viewModel.onPaused()

    pending.complete(Result.success(AT_1515))
    assertEquals("stop", player.calls.last())

    viewModel.onResumed()
    assertEquals("play ${AT_1515.fileName} at 0", player.calls.last())
  }

  @Test
  fun `status changes of a stopped player are ignored`() {
    open()
    viewModel.onPaused()

    player.status.value = PlaybackStatus.Failed(IllegalStateException("late"))

    assertTrue(state.status !is PlayerStatus.Failed)
  }

  private class FakeRecordingPlayer : RecordingPlayer {
    val calls = mutableListOf<String>()
    override val status = MutableStateFlow<PlaybackStatus>(PlaybackStatus.Idle)
    override var positionMillis = 0L

    override fun play(recording: Recording, positionMillis: Long) {
      calls += "play ${recording.fileName} at $positionMillis"
    }

    override fun stop() {
      calls += "stop"
    }

    override fun release() {
      calls += "release"
    }

    @Composable
    override fun Video(modifier: Modifier) = Unit
  }

  private class FakeTunnelCoordinator : TunnelCoordinator {
    val calls = mutableListOf<String>()
    val failures = MutableSharedFlow<Throwable>()
    override val connectFailures = failures

    override fun request(holder: TunnelHolder) {
      assertEquals(PLAYER_TUNNEL_HOLDER, holder)
      calls += "request"
    }

    override fun release(holder: TunnelHolder) {
      assertEquals(PLAYER_TUNNEL_HOLDER, holder)
      calls += "release"
    }

    override fun holdIfUp(holder: TunnelHolder) = throw UnsupportedOperationException()

    override fun clear() = throw UnsupportedOperationException()
  }
}
