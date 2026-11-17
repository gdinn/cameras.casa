package com.gdisys.cameras.core.recordings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.recordings.domain.model.Recording
import kotlinx.coroutines.flow.StateFlow

/**
 * Plays one recording at a time, straight from the share, and draws it.
 *
 * The contract sits at the package root rather than in `domain`, like `core/webrtc`'s: it owns a
 * Compose dependency ([Video]), which the domain rules forbid. Features talk to it and never to the
 * media3 implementation in `data`. Instrumented tests replace it with a fake.
 *
 * One instance per screen (`ViewModelComponent`), used from the main thread.
 */
interface RecordingPlayer {

  val status: StateFlow<PlaybackStatus>

  /** The position in the current recording, in milliseconds. */
  val positionMillis: Long

  /** Starts [recording] at [positionMillis], replacing whatever was playing. */
  fun play(recording: Recording, positionMillis: Long = 0)

  /** Stops and frees the player and its connection; [play] starts again. Status goes back to idle. */
  fun stop()

  /** [stop], for good. */
  fun release()

  /** The video, with the play/pause and seek controls. */
  @Composable
  fun Video(modifier: Modifier)
}

sealed interface PlaybackStatus {
  data object Idle : PlaybackStatus

  /** Opening the file, or buffering. */
  data object Loading : PlaybackStatus

  /** Playing or paused by the user. */
  data object Ready : PlaybackStatus

  /** The recording played to its end. */
  data object Ended : PlaybackStatus

  /** [cause] is a [RecordingsException] when the share failed, the player's own error otherwise. */
  data class Failed(val cause: Throwable) : PlaybackStatus
}
