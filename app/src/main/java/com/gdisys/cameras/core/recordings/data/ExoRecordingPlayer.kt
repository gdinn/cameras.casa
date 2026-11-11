package com.gdisys.cameras.core.recordings.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.gdisys.cameras.core.coroutines.ApplicationScope
import com.gdisys.cameras.core.coroutines.IoDispatcher
import com.gdisys.cameras.core.recordings.PlaybackStatus
import com.gdisys.cameras.core.recordings.RecordingPlayer
import com.gdisys.cameras.core.recordings.domain.RecordingsException
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.isValid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Buffer more than ExoPlayer's default before starting, and keep more ahead: the camera records at
 * ~1.3 Mbit/s and the tunnel carries ~33 Mbit/s, but a home upload can dip.
 */
private const val MIN_BUFFER_MS = 15_000
private const val MAX_BUFFER_MS = 60_000
private const val START_BUFFER_MS = 1_500
private const val REBUFFER_MS = 3_000

/**
 * [RecordingPlayer] over ExoPlayer and [SmbDataSource]. Every [play] builds a new ExoPlayer and a
 * new [SmbFileSession], so [stop] frees both: the screen stops the player when it pauses and plays
 * again when it resumes.
 *
 * Not unit-tested (excluded from coverage): ExoPlayer needs a device. The pieces with logic of their
 * own, [ReadAheadReader] and [SmbDataSource], are tested apart.
 */
@OptIn(UnstableApi::class)
class ExoRecordingPlayer @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val userPreferencesRepository: UserPreferencesRepository,
  @param:ApplicationScope private val scope: CoroutineScope,
  @param:IoDispatcher private val io: CoroutineDispatcher
) : RecordingPlayer {

  private val _status = MutableStateFlow<PlaybackStatus>(PlaybackStatus.Idle)
  override val status: StateFlow<PlaybackStatus> = _status.asStateFlow()

  /** Compose state, so [Video] attaches each new player. */
  private var player by mutableStateOf<ExoPlayer?>(null)
  private var session: SmbFileSession? = null

  override val positionMillis: Long get() = player?.currentPosition ?: 0

  private val listener = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
      _status.value = when (playbackState) {
        Player.STATE_BUFFERING -> PlaybackStatus.Loading
        Player.STATE_READY -> PlaybackStatus.Ready
        Player.STATE_ENDED -> PlaybackStatus.Ended
        else -> _status.value
      }
    }

    override fun onPlayerError(error: PlaybackException) {
      _status.value = PlaybackStatus.Failed(error.recordingsCause() ?: error)
    }
  }

  override fun play(recording: Recording, positionMillis: Long) {
    stop()
    val files = SmbFileSession {
      runBlocking { userPreferencesRepository.userPreferences.first().smbCredentials?.takeIf { it.isValid() } }
    }
    session = files
    _status.value = PlaybackStatus.Loading
    player = ExoPlayer.Builder(context)
      .setMediaSourceFactory(ProgressiveMediaSource.Factory(SmbDataSource.Factory(files)))
      .setLoadControl(
        DefaultLoadControl.Builder()
          .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, START_BUFFER_MS, REBUFFER_MS)
          .build()
      )
      .build()
      .apply {
        addListener(listener)
        setMediaItem(MediaItem.fromUri(recordingUri(recording)), positionMillis)
        playWhenReady = true
        prepare()
      }
  }

  override fun stop() {
    player?.run {
      removeListener(listener)
      release()
    }
    player = null
    // Closing logs off and closes a socket: not on the main thread.
    session?.let { files -> scope.launch(io) { files.close() } }
    session = null
    _status.value = PlaybackStatus.Idle
  }

  override fun release() = stop()

  @Composable
  override fun Video(modifier: Modifier) {
    val current = player
    AndroidView(
      factory = { viewContext ->
        PlayerView(viewContext).apply {
          setShowNextButton(false)
          setShowPreviousButton(false)
          setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
      },
      update = { view -> view.player = current },
      onRelease = { view -> view.player = null },
      modifier = modifier
    )
  }

  private fun PlaybackException.recordingsCause(): RecordingsException? =
    generateSequence<Throwable>(this) { it.cause }.filterIsInstance<RecordingReadException>().firstOrNull()?.reason
}
