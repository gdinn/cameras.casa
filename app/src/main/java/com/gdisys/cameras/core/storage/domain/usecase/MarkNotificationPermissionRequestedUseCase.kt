package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.StreamPreferencesRepository
import javax.inject.Inject

/**
 * Records that the notification permission was asked once, so it is never asked again: the user's
 * first answer stands.
 */
class MarkNotificationPermissionRequestedUseCase @Inject constructor(
  private val streamPreferencesRepository: StreamPreferencesRepository
) {
  suspend operator fun invoke(): Result<Unit> = try {
    streamPreferencesRepository.updateStreamPreferences { it.copy(notificationPermissionRequested = true) }
    Result.success(Unit)
  } catch (e: Exception) {
    Result.failure(e)
  }
}
