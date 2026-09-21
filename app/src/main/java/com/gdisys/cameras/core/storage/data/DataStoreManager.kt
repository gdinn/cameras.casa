package com.gdisys.cameras.core.storage.data

import androidx.datastore.core.DataStore
import com.gdisys.cameras.core.storage.di.UserPreferencesStore
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreManager @Inject constructor(
  @UserPreferencesStore private val dataStore: DataStore<UserPreferences>
) {
  val userPrefsState: Flow<UserPreferences> = dataStore.data

  /**
   * Rewrites the stored preferences through [transform], which receives the value persisted at
   * write time.
   *
   * Mirrors `StreamPreferencesDataStoreManager.updateStreamPreferences`. Taking a transform rather
   * than a finished object is what makes a partial update possible: the caller can rewrite one
   * slice of the file without having to read it first and race another writer.
   */
  suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences) {
    dataStore.updateData {
      transform(it)
    }
  }
}