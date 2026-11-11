package com.gdisys.cameras.feature.history.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import com.gdisys.cameras.MainDispatcherRule
import com.gdisys.cameras.core.recordings.PlaybackStatus
import com.gdisys.cameras.core.recordings.RecordingPlayer
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.recordings.domain.usecase.GetNeighborRecordingUseCase
import com.gdisys.cameras.core.recordings.domain.usecase.RecordingDirection
import com.gdisys.cameras.core.vpn.domain.TunnelCoordinator
import com.gdisys.cameras.core.vpn.domain.TunnelHolder
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.gdisys.cameras.core.vpn.domain.usecase.ObserveVpnStateUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

/**
 * The neighbor lookups with a dispatcher that queues coroutines, as `Dispatchers.Main` does. The
 * other player tests run unconfined, where a launched coroutine runs before `launch` returns; that
 * order once hid a lookup that, when done, saw itself still running and never played the neighbor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordingPlayerViewModelDispatchTest {

  private val dispatcher = StandardTestDispatcher()

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule(dispatcher)

  private val current = Recording("cam_10", LocalDateTime.of(2026, 11, 10, 23, 45), "cam_10_2026-11-10_23-45-00.mp4")
  private val next = Recording("cam_10", LocalDateTime.of(2026, 11, 11, 0, 0), "cam_10_2026-11-11_00-00-00.mp4")

  private val played = mutableListOf<String>()
  private val status = MutableStateFlow<PlaybackStatus>(PlaybackStatus.Idle)

  private val player = object : RecordingPlayer {
    override val status = this@RecordingPlayerViewModelDispatchTest.status
    override val positionMillis = 0L
    override fun play(recording: Recording, positionMillis: Long) {
      played += recording.fileName
    }
    override fun stop() = Unit
    override fun release() = Unit

    @Composable
    override fun Video(modifier: Modifier) = Unit
  }

  private val tunnelCoordinator = object : TunnelCoordinator {
    override val connectFailures = MutableSharedFlow<Throwable>()
    override fun request(holder: TunnelHolder) = Unit
    override fun release(holder: TunnelHolder) = Unit
    override fun holdIfUp(holder: TunnelHolder) = false
    override fun clear() = Unit
  }

  private fun newViewModel(): RecordingPlayerViewModel {
    val observeVpnStateUseCase = mockk<ObserveVpnStateUseCase>()
    every { observeVpnStateUseCase() } returns MutableStateFlow(VpnTunnelState.CONNECTED)
    val getNeighbor = mockk<GetNeighborRecordingUseCase>()
    coEvery { getNeighbor(current, RecordingDirection.NEXT) } returns Result.success(next)
    return RecordingPlayerViewModel(
      SavedStateHandle(mapOf(PLAYER_CAMERA_ARG to "cam_10", PLAYER_FILE_NAME_ARG to current.fileName)),
      observeVpnStateUseCase,
      getNeighbor,
      tunnelCoordinator,
      player
    )
  }

  @Test
  fun `next plays the neighbor once the lookup is done`() = runTest(dispatcher) {
    val viewModel = newViewModel()
    viewModel.onResumed()
    advanceUntilIdle()

    viewModel.onNext()
    advanceUntilIdle()

    assertEquals(listOf(current.fileName, next.fileName), played)
  }

  @Test
  fun `the end of a file plays the next one`() = runTest(dispatcher) {
    val viewModel = newViewModel()
    viewModel.onResumed()
    advanceUntilIdle()

    status.value = PlaybackStatus.Ended
    advanceUntilIdle()

    assertEquals(listOf(current.fileName, next.fileName), played)
  }
}
