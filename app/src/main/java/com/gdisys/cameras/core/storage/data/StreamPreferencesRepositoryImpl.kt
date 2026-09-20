package com.gdisys.cameras.core.storage.data

import android.util.Log
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.storage.domain.StreamPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamPreferencesRepositoryImpl @Inject constructor(
  private val streamPreferencesDataStoreManager: StreamPreferencesDataStoreManager
) : StreamPreferencesRepository {

  /**
   * Logs read failures and rethrows them: reporting the failure belongs to this layer, deciding
   * what the app shows instead is a domain call, made in `GetStreamPreferencesUseCase`.
   */
  override val streamPreferences: Flow<StreamPreferences> =
    streamPreferencesDataStoreManager.streamPrefsState
      .catch { e ->
        Log.e(DEBUG_TAG, "Failed to read stream preferences", e)
        throw e
      }

  override suspend fun updateStreamPreferences(
    transform: (StreamPreferences) -> StreamPreferences
  ) {
    streamPreferencesDataStoreManager.updateStreamPreferences(transform)
  }
}
