package com.gdisys.cameras.app.navigation

import kotlinx.serialization.Serializable

sealed interface NavigationRoute {
  @Serializable
  data object Config

  @Serializable
  data object Home

  /**
   * The recordings of [camera] (`cam_xx`), for admins. `HistoryViewModel` reads [camera] from its
   * `SavedStateHandle` under `HISTORY_CAMERA_ARG`, which must stay this property's name.
   */
  @Serializable
  data class History(val camera: String)

  /**
   * Plays [fileName] of [camera]. [nearestNotice] says the file does not start at the time the user
   * chose. `RecordingPlayerViewModel` reads the three from its `SavedStateHandle` under the
   * `PLAYER_*_ARG` names, which must stay these properties' names.
   */
  @Serializable
  data class RecordingPlayer(val camera: String, val fileName: String, val nearestNotice: Boolean)

  @Serializable
  data object Loading

  @Serializable
  data object QrCode

  @Serializable
  data object StreamURLs
}