package com.gdisys.cameras.feature.cameras

import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation

sealed interface HomeUiState {
  data object Loading : HomeUiState

  /** Nenhuma URL cadastrada: não há o que conectar, só o convite a configurar. */
  data object Empty : HomeUiState

  /**
   * @property streams URLs na ordem persistida para [orientation]
   * @property grid configuração de grade da orientação corrente
   */
  data class Ready(
    val streams: List<String>,
    val focusedStream: String?,
    val grid: GridPreferences,
    val orientation: StreamOrientation
  ) : HomeUiState
}
