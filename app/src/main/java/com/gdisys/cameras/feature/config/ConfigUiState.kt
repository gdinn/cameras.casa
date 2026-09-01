package com.gdisys.cameras.feature.config

sealed interface ConfigUiState {
  data object Loading : ConfigUiState
  data object NeedsConfiguration : ConfigUiState
  data object Scanning : ConfigUiState
  data object ConfigurationLoaded : ConfigUiState
}
