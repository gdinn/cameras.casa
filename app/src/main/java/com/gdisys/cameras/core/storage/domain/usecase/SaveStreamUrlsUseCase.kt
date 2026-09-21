package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.StreamPreferencesRepository
import javax.inject.Inject

/**
 * Sobrescreve o conjunto canônico de URLs e as duas ordens de exibição, preservando a configuração
 * das grades.
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
