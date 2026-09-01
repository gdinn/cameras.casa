package com.gdisys.cameras.core.vpn.domain.usecase

import android.content.Intent
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import javax.inject.Inject

class RequestVpnPermissionUseCase @Inject constructor(
  private val vpnRepository: VpnRepository
) {
  operator fun invoke(): Intent? = vpnRepository.getVpnPermissionIntent()
}
