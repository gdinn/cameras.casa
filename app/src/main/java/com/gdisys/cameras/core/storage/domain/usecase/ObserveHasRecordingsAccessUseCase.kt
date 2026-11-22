package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.isValid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Whether the stored credentials include a usable Samba login, which is what gives the recordings
 * (history, playback and downloads) to a peer, admin or client.
 */
class ObserveHasRecordingsAccessUseCase @Inject constructor(
  private val userPreferencesRepository: UserPreferencesRepository
) {
  operator fun invoke(): Flow<Boolean> = userPreferencesRepository.userPreferences
    .map { it.smbCredentials?.isValid() == true }
    .distinctUntilChanged()
}
