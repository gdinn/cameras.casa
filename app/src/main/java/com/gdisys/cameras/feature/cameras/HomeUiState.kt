package com.gdisys.cameras.feature.cameras

import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation

sealed interface HomeUiState {
  data object Loading : HomeUiState

  /** No URL registered: nothing to connect to, only the invitation to configure one. */
  data object Empty : HomeUiState

  /**
   * @property streams URLs in the order persisted for [orientation]
   * @property grid grid configuration of the current orientation
   * @property failedStreams the [streams] whose last connection attempt failed; their cards show
   *   the error and a retry button instead of a black frame
   * @property historyCamera the camera (`cam_xx`) whose recordings the focused view offers. Only
   *   for users with a Samba login, and only when the focused stream's URL names a camera
   */
  data class Ready(
    val streams: List<String>,
    val focusedStream: String?,
    val grid: GridPreferences,
    val orientation: StreamOrientation,
    val failedStreams: Set<String> = emptySet(),
    val historyCamera: String? = null
  ) : HomeUiState
}
