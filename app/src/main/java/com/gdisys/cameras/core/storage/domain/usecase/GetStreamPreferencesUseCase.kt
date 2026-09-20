package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.StreamPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.StreamPreferences
import com.gdisys.cameras.core.storage.domain.model.reconciled
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Observa as preferências de stream já reconciliadas — quem lê nunca vê uma ordem divergente do
 * conjunto canônico de URLs.
 *
 * A read failure emits empty preferences instead of propagating: collectors combine this flow into
 * their UI state, so an exception would cancel that collection and freeze the screen on its
 * initial state with no feedback. Empty preferences degrade to the "no URLs configured" screen,
 * which is recoverable by the user. This guarantee is stated here rather than left to whatever the
 * storage implementation happens to do with a corrupt file.
 */
class GetStreamPreferencesUseCase @Inject constructor(
  private val streamPreferencesRepository: StreamPreferencesRepository
) {
  operator fun invoke(): Flow<StreamPreferences> =
    streamPreferencesRepository.streamPreferences
      .map { it.reconciled() }
      .catch { emit(StreamPreferences()) }
}
