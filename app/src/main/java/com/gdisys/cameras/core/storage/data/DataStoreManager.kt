package com.gdisys.cameras.core.storage.data

import androidx.datastore.core.DataStore
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreManager @Inject constructor(
  private val dataStore: DataStore<UserPreferences>
) {
  val userPrefsState: Flow<UserPreferences> = dataStore.data

  suspend fun updateUserPreferences(userPreferences: UserPreferences) {
    dataStore.updateData {
      userPreferences
    }
  }
}