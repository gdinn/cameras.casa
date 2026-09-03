package com.gdisys.cameras.core.vpn.data

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private class AppTunnel(
  private val tunnelName: String = "wg0",
  private val onStateChanged: (Tunnel.State) -> Unit
) : Tunnel {
  override fun getName() = tunnelName

  override fun onStateChange(newState: Tunnel.State) {
    onStateChanged(newState)
    Log.d(DEBUG_TAG, "Status do Túnel alterado para: $newState")
  }
}

fun Tunnel.State.toVpnTunnelState(): VpnTunnelState {
  return when (this) {
    Tunnel.State.UP -> VpnTunnelState.CONNECTED
    Tunnel.State.DOWN -> VpnTunnelState.DISCONNECTED
    Tunnel.State.TOGGLE -> VpnTunnelState.CONNECTING
  }
}

/**
 * Mapeamento puro `VpnConfig` -> `Config` do WireGuard, extraído de [VpnRepositoryImpl.connect]
 * para ser testável sem instanciar `GoBackend` (lib nativa, não roda em JVM puro).
 */
fun VpnConfig.toWireGuardConfig(): Config {
  val interfaceBuilder = Interface.Builder()
    .parsePrivateKey(privateKey)
    .parseAddresses(address)
    .parseDnsServers(dns)
    .parseMtu(mtu)

  val peerBuilder = Peer.Builder()
    .parsePublicKey(publicKey)
    .parsePreSharedKey(preSharedKey)
    .parseAllowedIPs(allowedIps)
    .parseEndpoint(endpoint)
    .parsePersistentKeepalive(keepAlive)

  return Config.Builder()
    .setInterface(interfaceBuilder.build())
    .addPeer(peerBuilder.build())
    .build()
}

@Singleton
class VpnRepositoryImpl @Inject constructor(
  @ApplicationContext private val context: Context
) : VpnRepository {

  // É altamente recomendado manter apenas uma instância do Backend durante o ciclo de vida do app.
  private val backend: Backend by lazy { GoBackend(context) }

  private val _vpnState = MutableStateFlow(VpnTunnelState.DISCONNECTED)
  override val vpnState: StateFlow<VpnTunnelState> = _vpnState.asStateFlow()

  private val tunnel = AppTunnel { newState ->
    _vpnState.value = newState.toVpnTunnelState()
  }

  init {
    // Tenta pegar o estado inicial se o backend já estiver pronto ou assim que possível
    // No caso do GoBackend, podemos consultar o estado atual.
    _vpnState.value = backend.getState(tunnel).toVpnTunnelState()
  }

  /**
   * Inicia a conexão com os parâmetros do servidor WireGuard.
   */
  override suspend fun connect(
    config: VpnConfig
  ) = withContext<Unit>(Dispatchers.IO) {
    _vpnState.value = VpnTunnelState.CONNECTING
    backend.setState(tunnel, Tunnel.State.UP, config.toWireGuardConfig())
  }

  /**
   * Derruba a conexão do túnel atual.
   */
  override suspend fun disconnect() = withContext<Unit>(Dispatchers.IO) {
    backend.setState(tunnel, Tunnel.State.DOWN, null)
  }

  /**
   * Retorna o estado atual (CONNECTED, DISCONNECTED ou CONNECTING).
   */
  override fun getTunnelState(): VpnTunnelState {
    return backend.getState(tunnel).toVpnTunnelState()
  }

  override fun getVpnPermissionIntent(): Intent? = VpnService.prepare(context)
}