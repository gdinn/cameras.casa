package com.gdisys.cameras.core.vpn.domain

import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import kotlinx.coroutines.flow.StateFlow

interface VpnRepository {
  val vpnState: StateFlow<VpnTunnelState>
  suspend fun connect(config: VpnConfig)
  suspend fun disconnect()
  fun getTunnelState(): VpnTunnelState
}