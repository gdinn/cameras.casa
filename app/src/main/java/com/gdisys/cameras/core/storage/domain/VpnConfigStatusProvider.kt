package com.gdisys.cameras.core.storage.domain

import com.gdisys.cameras.core.storage.domain.model.isInvalid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class VpnConfigStatusProvider @Inject constructor(
  userPreferencesRepository: UserPreferencesRepository
) {
  val uiState: Flow<VpnDataUiState> = userPreferencesRepository.userPreferences.map { prefs ->
    VpnDataUiState.Success(
      vpnConfigTokensEmpty =
        prefs.vpnConfigTokens?.isInvalid() ?: true ||
          prefs.vpnConfigDefaults?.isInvalid() ?: true
    )
  }
}