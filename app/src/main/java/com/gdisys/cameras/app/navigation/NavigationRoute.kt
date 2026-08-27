package com.gdisys.cameras.app.navigation

import kotlinx.serialization.Serializable

sealed interface NavigationRoute {
  @Serializable
  data object ConfigGraph

  @Serializable
  data object Config

  @Serializable
  data object Home

  @Serializable
  data object Loading
}