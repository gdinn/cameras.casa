package com.gdisys.cameras.feature.cameras

sealed interface HomeUiState {
  data object Loading : HomeUiState

  data class Ready(
    val streams: List<String>,
    val focusedStream: String?
  ) : HomeUiState
}
