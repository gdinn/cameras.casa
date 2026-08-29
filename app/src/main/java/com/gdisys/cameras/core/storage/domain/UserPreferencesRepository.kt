package com.gdisys.cameras.core.storage.domain

import com.gdisys.cameras.core.storage.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
  val userPreferences: Flow<UserPreferences>
  suspend fun updateUserPreferences(userPreferences: UserPreferences)
}
