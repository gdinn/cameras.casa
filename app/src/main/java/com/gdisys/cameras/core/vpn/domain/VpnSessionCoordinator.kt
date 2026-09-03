package com.gdisys.cameras.core.vpn.domain

import com.gdisys.cameras.core.storage.domain.usecase.GetVpnConfigUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.ConnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.DisconnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.ObserveVpnStateUseCase
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordena a sessão de VPN compartilhada pelas telas que dependem dela (Home e
 * Histórico), evitando reconectar um túnel que já está ativo.
 */
@Singleton
class VpnSessionCoordinator @Inject constructor(
  observeVpnStateUseCase: ObserveVpnStateUseCase,
  private val connectVpnUseCase: ConnectVpnUseCase,
  private val disconnectVpnUseCase: DisconnectVpnUseCase,
  private val getVpnConfigUseCase: GetVpnConfigUseCase
) {
  val vpnState: StateFlow<VpnTunnelState> = observeVpnStateUseCase()

  suspend fun connect(): Result<Unit> {
    if (vpnState.value == VpnTunnelState.CONNECTED) return Result.success(Unit)

    return getVpnConfigUseCase().fold(
      onSuccess = { config -> connectVpnUseCase(config) },
      onFailure = { e -> Result.failure(e) }
    )
  }

  suspend fun disconnect(): Result<Unit> = disconnectVpnUseCase()
}
