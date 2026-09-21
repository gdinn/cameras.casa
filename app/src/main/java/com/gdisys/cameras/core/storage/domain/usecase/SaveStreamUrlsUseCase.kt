package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.StreamPreferencesRepository
import javax.inject.Inject

/**
 * Overwrites the canonical set of URLs and both display orders, preserving the grid configuration.
 */
class SaveStreamUrlsUseCase @Inject constructor(
  private val streamPreferencesRepository: StreamPreferencesRepository
) {
  suspend operator fun invoke(
    streamUrls: List<String>,
    portraitOrder: List<String>,
    landscapeOrder: List<String>
  ): Result<Unit> = try {
    streamPreferencesRepository.updateStreamPreferences { current ->
      current.copy(
        streamUrls = streamUrls,
        portraitOrder = portraitOrder,
        landscapeOrder = landscapeOrder
      )
    }
    Result.success(Unit)
  } catch (e: Exception) {
    Result.failure(e)
  }
}
