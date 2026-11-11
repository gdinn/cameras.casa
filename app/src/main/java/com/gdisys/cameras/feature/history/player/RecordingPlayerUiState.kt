package com.gdisys.cameras.feature.history.player

import androidx.annotation.StringRes
import com.gdisys.cameras.R
import com.gdisys.cameras.core.ToastMessage
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.recordings.domain.usecase.RecordingDirection
import com.gdisys.cameras.feature.history.HistoryError

/**
 * @property recording the recording on screen
 * @property nearestNotice whether to say that [recording] does not start at the time the user chose;
 *   only for the first recording, until the user moves to another
 * @property looking the side being looked up for a neighbor, which disables both buttons
 */
data class RecordingPlayerUiState(
  val recording: Recording,
  val nearestNotice: Boolean,
  val status: PlayerStatus = PlayerStatus.Loading,
  val looking: RecordingDirection? = null
)

sealed interface PlayerStatus {
  /** Waiting for the tunnel, opening the file or buffering. */
  data object Loading : PlayerStatus

  /** Playing, or paused with the player's controls. */
  data object Playing : PlayerStatus

  /** The newest finished recording played to its end: there is nothing to advance to. */
  data object Ended : PlayerStatus

  data class Failed(val error: HistoryError) : PlayerStatus
}

enum class RecordingPlayerToastMessage(@get:StringRes override val resId: Int) : ToastMessage {
  NO_EARLIER_RECORDING(R.string.player_no_earlier_recording),
  NO_LATER_RECORDING(R.string.player_no_later_recording),
  NEIGHBOR_LOOKUP_FAILED(R.string.player_neighbor_lookup_failed)
}
