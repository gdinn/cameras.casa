package com.gdisys.cameras.core.storage.data

import com.gdisys.cameras.core.storage.domain.StreamPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamPreferencesRepositoryImpl @Inject constructor(
  private val streamPreferencesDataStoreManager: StreamPreferencesDataStoreManager
) : StreamPreferencesRepository {

  override val streamPreferences: Flow<StreamPreferences> =
    streamPreferencesDataStoreManager.streamPrefsState

  override suspend fun updateStreamPreferences(
    transform: (StreamPreferences) -> StreamPreferences
  ) {
    streamPreferencesDataStoreManager.updateStreamPreferences(transform)
  }
}
