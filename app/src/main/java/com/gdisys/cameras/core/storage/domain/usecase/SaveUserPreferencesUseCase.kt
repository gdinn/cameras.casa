package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import javax.inject.Inject

class SaveUserPreferencesUseCase @Inject constructor(
  private val userPreferencesRepository: UserPreferencesRepository
) {
  suspend operator fun invoke(userPreferences: UserPreferences) {
    userPreferencesRepository.updateUserPreferences(userPreferences)
  }
}
