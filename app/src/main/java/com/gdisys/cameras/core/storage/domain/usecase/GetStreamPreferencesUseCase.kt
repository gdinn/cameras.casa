package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.StreamPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import com.gdisys.cameras.core.storage.domain.model.reconciled
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Observa as preferências de stream já reconciliadas — quem lê nunca vê uma ordem divergente do
 * conjunto canônico de URLs.
 */
class GetStreamPreferencesUseCase @Inject constructor(
  private val streamPreferencesRepository: StreamPreferencesRepository
) {
  operator fun invoke(): Flow<StreamPreferences> =
    streamPreferencesRepository.streamPreferences.map { it.reconciled() }
}
