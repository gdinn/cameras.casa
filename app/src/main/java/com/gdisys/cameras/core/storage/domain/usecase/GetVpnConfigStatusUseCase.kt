package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import com.gdisys.cameras.core.storage.domain.VpnCredentialsStatus
import com.gdisys.cameras.core.storage.domain.model.isInvalid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetVpnConfigStatusUseCase @Inject constructor(
  private val userPreferencesRepository: UserPreferencesRepository
) {
  operator fun invoke(): Flow<VpnCredentialsStatus> = userPreferencesRepository.userPreferences.map { prefs ->
    val tokensInvalid = prefs.vpnConfigTokens?.isInvalid() ?: true
    val defaultsInvalid = prefs.vpnConfigDefaults?.isInvalid() ?: true
    VpnCredentialsStatus.Loaded(hasValidCredentials = !(tokensInvalid || defaultsInvalid))
  }
}
