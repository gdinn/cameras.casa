package com.gdisys.cameras.feature.history.player

import androidx.annotation.StringRes
import com.gdisys.cameras.R
import com.gdisys.cameras.core.ToastMessage
import com.gdisys.cameras.core.downloads.domain.model.DownloadStatus
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.recordings.domain.usecase.RecordingDirection
import com.gdisys.cameras.feature.history.HistoryError

/**
 * @property recording the recording on screen
 * @property nearestNotice whether to say that [recording] does not start at the time the user chose;
 *   only for the first recording, until the user moves to another
 * @property looking the side being looked up for a neighbor, which disables both buttons
 * @property download the latest download of [recording], `null` when there is none
 */
data class RecordingPlayerUiState(
  val recording: Recording,
  val nearestNotice: Boolean,
  val status: PlayerStatus = PlayerStatus.Loading,
  val looking: RecordingDirection? = null,
  val download: DownloadStatus? = null
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
  NEIGHBOR_LOOKUP_FAILED(R.string.player_neighbor_lookup_failed),
  DOWNLOAD_STARTED(R.string.player_download_started),
  DOWNLOAD_ALREADY_RUNNING(R.string.player_download_already_running),
  DOWNLOAD_TUNNEL_DOWN(R.string.player_download_tunnel_down),
  DOWNLOAD_STORAGE_DENIED(R.string.player_download_storage_denied)
}
