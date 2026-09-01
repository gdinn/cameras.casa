package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import com.gdisys.cameras.core.storage.domain.VpnDataUiState
import com.gdisys.cameras.core.storage.domain.model.isInvalid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetVpnConfigStatusUseCase @Inject constructor(
  private val userPreferencesRepository: UserPreferencesRepository
) {
  operator fun invoke(): Flow<VpnDataUiState> = userPreferencesRepository.userPreferences.map { prefs ->
    VpnDataUiState.Success(
      vpnConfigTokensEmpty =
        prefs.vpnConfigTokens?.isInvalid() ?: true ||
          prefs.vpnConfigDefaults?.isInvalid() ?: true
    )
  }
}
