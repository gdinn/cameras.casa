package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import com.gdisys.cameras.core.storage.domain.toVpnConfigOrNull
import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetVpnConfigUseCase @Inject constructor(
  private val userPreferencesRepository: UserPreferencesRepository
) {
  suspend operator fun invoke(): VpnConfig? =
    userPreferencesRepository.userPreferences.first().toVpnConfigOrNull()
}
