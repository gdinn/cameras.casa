package com.gdisys.cameras.core.storage.domain

import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import kotlinx.coroutines.flow.Flow

interface StreamPreferencesRepository {
  val streamPreferences: Flow<StreamPreferences>

  /**
   * Atualiza as preferências atomicamente. O [transform] recebe o valor persistido no momento da
   * escrita, o que permite que cada use case altere apenas o seu escopo sem sobrescrever o resto.
   */
  suspend fun updateStreamPreferences(transform: (StreamPreferences) -> StreamPreferences)
}
