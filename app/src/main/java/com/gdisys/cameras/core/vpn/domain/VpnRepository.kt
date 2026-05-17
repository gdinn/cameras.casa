package com.gdisys.cameras.core.vpn.domain

import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.flow.StateFlow

interface VpnRepository {
    val vpnState: StateFlow<Tunnel.State>
    suspend fun connect(config: VpnConfig)
    suspend fun disconnect()
    fun getTunnelState(): Tunnel.State
}