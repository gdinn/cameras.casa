package com.gdisys.cameras.core.vpn.domain.usecase

import com.gdisys.cameras.core.vpn.domain.VpnRepository
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveVpnStateUseCase @Inject constructor(
  private val vpnRepository: VpnRepository
) {
  operator fun invoke(): StateFlow<VpnTunnelState> = vpnRepository.vpnState
}
