package com.gdisys.cameras.core.permission.domain.usecase

import com.gdisys.cameras.core.permission.domain.VpnConsent
import com.gdisys.cameras.core.permission.domain.VpnPermissionRepository
import javax.inject.Inject

class RequestVpnPermissionUseCase @Inject constructor(
  private val vpnPermissionRepository: VpnPermissionRepository
) {
  operator fun invoke(): VpnConsent = vpnPermissionRepository.getVpnConsent()
}
