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

  @Serializable
  data object Loading

  @Serializable
  data object QrCode

  @Serializable
  data object StreamURLs
}