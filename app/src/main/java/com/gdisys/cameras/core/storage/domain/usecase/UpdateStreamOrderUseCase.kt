package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.StreamPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import javax.inject.Inject

/**
 * Persiste a nova ordem de exibição de **uma** orientação; a ordem da outra permanece intacta.
 */
class UpdateStreamOrderUseCase @Inject constructor(
  private val streamPreferencesRepository: StreamPreferencesRepository
) {
  suspend operator fun invoke(
    orientation: StreamOrientation,
    order: List<String>
  ): Result<Unit> = try {
    streamPreferencesRepository.updateStreamPreferences { current ->
      when (orientation) {
        StreamOrientation.PORTRAIT -> current.copy(portraitOrder = order)
        StreamOrientation.LANDSCAPE -> current.copy(landscapeOrder = order)
      }
    }
    Result.success(Unit)
  } catch (e: Exception) {
    Result.failure(e)
  }
}
