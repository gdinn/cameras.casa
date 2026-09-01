package com.gdisys.cameras.core.storage.data

import com.gdisys.cameras.core.storage.data.DataStoreManager
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
  private val dataStoreManager: DataStoreManager
) : UserPreferencesRepository {

  override val userPreferences: Flow<UserPreferences> = dataStoreManager.userPrefsState

  override suspend fun updateUserPreferences(userPreferences: UserPreferences) {
    dataStoreManager.updateUserPreferences(userPreferences)
  }
}
