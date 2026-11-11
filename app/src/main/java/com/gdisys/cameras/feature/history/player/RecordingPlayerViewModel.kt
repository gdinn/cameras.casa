package com.gdisys.cameras.feature.history.player

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.ToastEventViewModel
import com.gdisys.cameras.core.recordings.PlaybackStatus
import com.gdisys.cameras.core.recordings.RecordingPlayer
import com.gdisys.cameras.core.recordings.domain.RecordingFileName
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.recordings.domain.usecase.GetNeighborRecordingUseCase
import com.gdisys.cameras.core.recordings.domain.usecase.RecordingDirection
import com.gdisys.cameras.core.vpn.domain.TunnelCoordinator
import com.gdisys.cameras.core.vpn.domain.TunnelHolder
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.gdisys.cameras.core.vpn.domain.usecase.ObserveVpnStateUseCase
import com.gdisys.cameras.feature.history.HistoryError
import com.gdisys.cameras.feature.history.toHistoryError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The route arguments. Type-safe navigation stores a route's properties in the `SavedStateHandle`
 * under their names, so these are the names of `NavigationRoute.RecordingPlayer`'s properties.
 */
const val PLAYER_CAMERA_ARG = "camera"
const val PLAYER_FILE_NAME_ARG = "fileName"
const val PLAYER_NEAREST_NOTICE_ARG = "nearestNotice"

/** The player's registration with the [TunnelCoordinator]. */
internal val PLAYER_TUNNEL_HOLDER = TunnelHolder.Screen("recording-player")

/**
 * Plays one recording and moves to its neighbors.
 *
 * - **Tunnel:** held while resumed, like the other screens. The player starts once the tunnel is
 *   up and the screen resumed.
 * - **Pause:** the player is stopped, which frees ExoPlayer and its SMB connection, and the position
 *   is kept. Resuming plays again from there.
 * - **Previous/next:** look up the neighbor across days; at either end a toast says there is none.
 * - **End of a file:** advances to the next one by itself. After the newest finished file the
 *   status is [PlayerStatus.Ended].
 * - **Failure:** a read or tunnel failure stops the player and keeps the position for the retry.
 *
 * The current file name is written back to the `SavedStateHandle`, so a recreated screen shows the
 * file the user moved to.
 */
@HiltViewModel
class RecordingPlayerViewModel @Inject constructor(
  private val savedStateHandle: SavedStateHandle,
  observeVpnStateUseCase: ObserveVpnStateUseCase,
  private val getNeighborRecordingUseCase: GetNeighborRecordingUseCase,
  private val tunnelCoordinator: TunnelCoordinator,
  val player: RecordingPlayer
) : ToastEventViewModel() {

  private val _uiState = MutableStateFlow(
    RecordingPlayerUiState(
      recording = recordingFromArguments(),
      nearestNotice = savedStateHandle[PLAYER_NEAREST_NOTICE_ARG] ?: false
    )
  )
  val uiState: StateFlow<RecordingPlayerUiState> = _uiState.asStateFlow()

  private var resumed = false
  private var tunnelUp = false

  /** Whether [player] was told to play the current recording and was not stopped since. */
  private var started = false
  private var resumePosition = 0L

  init {
    viewModelScope.launch {
      observeVpnStateUseCase().collect { state ->
        tunnelUp = state == VpnTunnelState.CONNECTED
        startIfReady()
      }
    }

    viewModelScope.launch { player.status.collect(::onPlaybackStatus) }

    viewModelScope.launch {
      tunnelCoordinator.connectFailures.collect { e ->
        if (!resumed || started || _uiState.value.status != PlayerStatus.Loading) return@collect
        Log.d(DEBUG_TAG, e.message.toString())
        setStatus(PlayerStatus.Failed(HistoryError.VPN_CONNECTION))
      }
    }
  }

  fun onResumed() {
    resumed = true
    tunnelCoordinator.request(PLAYER_TUNNEL_HOLDER)
    startIfReady()
  }

  fun onPaused() {
    resumed = false
    stopKeepingPosition()
    tunnelCoordinator.release(PLAYER_TUNNEL_HOLDER)
  }

  fun onPrevious() = move(RecordingDirection.PREVIOUS, automatic = false)

  fun onNext() = move(RecordingDirection.NEXT, automatic = false)

  /** Plays again from where it stopped. Asking for the tunnel again retries a failed connect. */
  fun onRetry() {
    if (_uiState.value.status !is PlayerStatus.Failed) return
    setStatus(PlayerStatus.Loading)
    if (resumed) tunnelCoordinator.request(PLAYER_TUNNEL_HOLDER)
    startIfReady()
  }

  override fun onCleared() {
    player.release()
    tunnelCoordinator.release(PLAYER_TUNNEL_HOLDER)
    super.onCleared()
  }

  private fun startIfReady() {
    val state = _uiState.value
    // `looking`, not the lookup's job: the job itself calls this once it is done, while still active.
    if (!resumed || !tunnelUp || started || state.status !is PlayerStatus.Loading || state.looking != null) return
    started = true
    player.play(state.recording, resumePosition)
  }

  private fun stopKeepingPosition() {
    if (!started) return
    resumePosition = player.positionMillis
    started = false
    player.stop()
  }

  private fun onPlaybackStatus(status: PlaybackStatus) {
    if (!started) return
    when (status) {
      PlaybackStatus.Idle -> Unit
      PlaybackStatus.Loading -> setStatus(PlayerStatus.Loading)
      PlaybackStatus.Ready -> setStatus(PlayerStatus.Playing)
      PlaybackStatus.Ended -> move(RecordingDirection.NEXT, automatic = true)
      is PlaybackStatus.Failed -> {
        Log.d(DEBUG_TAG, "Playing ${_uiState.value.recording.fileName} failed: ${status.cause}")
        stopKeepingPosition()
        setStatus(PlayerStatus.Failed(status.cause.toHistoryError()))
      }
    }
  }

  private fun move(direction: RecordingDirection, automatic: Boolean) {
    if (_uiState.value.looking != null) return
    val current = _uiState.value.recording
    _uiState.update { it.copy(looking = direction) }
    viewModelScope.launch {
      getNeighborRecordingUseCase(current, direction).fold(
        onSuccess = { neighbor ->
          when {
            neighbor != null -> switchTo(neighbor)
            automatic -> {
              stopKeepingPosition()
              setStatus(PlayerStatus.Ended)
            }
            direction == RecordingDirection.PREVIOUS -> showToast(RecordingPlayerToastMessage.NO_EARLIER_RECORDING)
            else -> showToast(RecordingPlayerToastMessage.NO_LATER_RECORDING)
          }
        },
        onFailure = { e ->
          Log.d(DEBUG_TAG, "Looking up the $direction recording failed: $e")
          showToast(RecordingPlayerToastMessage.NEIGHBOR_LOOKUP_FAILED)
          if (automatic) {
            stopKeepingPosition()
            setStatus(PlayerStatus.Ended)
          }
        }
      )
      _uiState.update { it.copy(looking = null) }
      startIfReady()
    }
  }

  private fun switchTo(recording: Recording) {
    if (started) {
      started = false
      player.stop()
    }
    resumePosition = 0
    savedStateHandle[PLAYER_FILE_NAME_ARG] = recording.fileName
    _uiState.update { it.copy(recording = recording, nearestNotice = false, status = PlayerStatus.Loading) }
  }

  private fun setStatus(status: PlayerStatus) = _uiState.update { it.copy(status = status) }

  private fun recordingFromArguments(): Recording {
    val camera: String = checkNotNull(savedStateHandle[PLAYER_CAMERA_ARG]) { "The player opened without a camera" }
    val fileName: String = checkNotNull(savedStateHandle[PLAYER_FILE_NAME_ARG]) { "The player opened without a file" }
    val start = checkNotNull(RecordingFileName.parse(camera, fileName)) { "Not a recording of $camera: $fileName" }
    return Recording(camera, start, fileName)
  }
}
