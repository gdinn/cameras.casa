package com.gdisys.cameras.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gdisys.cameras.core.recordings.PlaybackStatus
import com.gdisys.cameras.core.recordings.RecordingPlayer
import com.gdisys.cameras.core.recordings.domain.model.Recording
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/** What every player was asked to play, in order. Tests inject it: it is shared by all the fakes. */
@Singleton
class FakeRecordingPlayback @Inject constructor() {
  /** The file names played, in order. */
  val played: MutableList<String> = CopyOnWriteArrayList()
}

/**
 * A player that never reads the share: [play] records the file in [FakeRecordingPlayback] and is
 * ready at once, and the video is an empty box. One per ViewModel, like the real one.
 */
class FakeRecordingPlayer @Inject constructor(
  private val playback: FakeRecordingPlayback
) : RecordingPlayer {

  override val status = MutableStateFlow<PlaybackStatus>(PlaybackStatus.Idle)
  override val positionMillis: Long = 0

  override fun play(recording: Recording, positionMillis: Long) {
    playback.played += recording.fileName
    status.value = PlaybackStatus.Ready
  }

  override fun stop() {
    status.value = PlaybackStatus.Idle
  }

  override fun release() = stop()

  @Composable
  override fun Video(modifier: Modifier) {
    Box(modifier)
  }
}
