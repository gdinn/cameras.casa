package com.gdisys.cameras.core.vpn.domain

import android.content.Intent
import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import kotlinx.coroutines.flow.StateFlow

interface VpnRepository {
  val vpnState: StateFlow<VpnTunnelState>
  suspend fun connect(config: VpnConfig)
  suspend fun disconnect()
  fun getTunnelState(): VpnTunnelState

  /**
   * Returns the system consent `Intent` needed to bring the VPN up, or `null` when permission has
   * already been granted — the contract of `VpnService.prepare`.
   */
  fun getVpnPermissionIntent(): Intent?
}