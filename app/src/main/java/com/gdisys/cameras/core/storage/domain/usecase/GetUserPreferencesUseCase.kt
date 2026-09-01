package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetUserPreferencesUseCase @Inject constructor(
  private val userPreferencesRepository: UserPreferencesRepository
) {
  operator fun invoke(): Flow<UserPreferences> = userPreferencesRepository.userPreferences
}
