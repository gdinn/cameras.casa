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
   * Retorna o `Intent` de consentimento do sistema para ativar a VPN, ou `null`
   * se a permissão já foi concedida (contrato de `VpnService.prepare`).
   */
  fun getVpnPermissionIntent(): Intent?
}