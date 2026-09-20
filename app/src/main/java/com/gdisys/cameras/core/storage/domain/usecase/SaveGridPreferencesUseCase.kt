package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.StreamPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import javax.inject.Inject

/** Sobrescreve as grades das duas orientações, sem tocar nas URLs nem nas ordens. */
class SaveGridPreferencesUseCase @Inject constructor(
  private val streamPreferencesRepository: StreamPreferencesRepository
) {
  suspend operator fun invoke(
    portraitGrid: GridPreferences,
    landscapeGrid: GridPreferences
  ): Result<Unit> = try {
    streamPreferencesRepository.updateStreamPreferences { current ->
      current.copy(
        portraitGrid = portraitGrid,
        landscapeGrid = landscapeGrid
      )
    }
    Result.success(Unit)
  } catch (e: Exception) {
    Result.failure(e)
  }
}
