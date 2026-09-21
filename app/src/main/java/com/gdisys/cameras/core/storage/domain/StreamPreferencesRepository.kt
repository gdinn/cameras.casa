package com.gdisys.cameras.core.storage.domain

import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import kotlinx.coroutines.flow.Flow

interface StreamPreferencesRepository {
  val streamPreferences: Flow<StreamPreferences>

  /**
   * Updates the preferences atomically. [transform] receives the value persisted at write time,
   * which is what lets each use case rewrite only its own slice without clobbering the rest.
   */
  suspend fun updateStreamPreferences(transform: (StreamPreferences) -> StreamPreferences)
}
