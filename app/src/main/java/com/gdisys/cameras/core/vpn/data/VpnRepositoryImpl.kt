package com.gdisys.cameras.core.vpn.data

import android.content.Context
import android.content.Intent
import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import com.gdisys.cameras.core.vpn.domain.VpnRepository
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
    println("Status do Túnel alterado para: $newState")
  }
}

@Singleton
class VpnRepositoryImpl @Inject constructor(
  @ApplicationContext private val context: Context
) : VpnRepository {

  // É altamente recomendado manter apenas uma instância do Backend durante o ciclo de vida do app.
  private val backend: Backend by lazy { GoBackend(context) }

  private val _vpnState = MutableStateFlow(Tunnel.State.DOWN)
  override val vpnState: StateFlow<Tunnel.State> = _vpnState.asStateFlow()

  private val tunnel = AppTunnel { newState ->
    _vpnState.value = newState
  }

  init {
    // Tenta pegar o estado inicial se o backend já estiver pronto ou assim que possível
    // No caso do GoBackend, podemos consultar o estado atual.
    _vpnState.value = backend.getState(tunnel)
  }

  /**
   * Inicia a conexão com os parâmetros do servidor WireGuard.
   */
  override suspend fun connect(
    config: VpnConfig
  ) = withContext<Unit>(Dispatchers.IO) {
    // 1. Configuração da Interface (Cliente)
    val interfaceBuilder = Interface.Builder()
      .parsePrivateKey(config.privateKey)
      .parseAddresses(config.address)
      .parseDnsServers(config.dns)
      .addDnsSearchDomain(config.dnsSearchDomain)
      .parseMtu(config.mtu)

    // 2. Configuração do Peer (Servidor)
    val peerBuilder = Peer.Builder()
      .parsePublicKey(config.publicKey)
      .parsePreSharedKey(config.preSharedKey)
      .parseAllowedIPs(config.allowedIps)
      .parseEndpoint(config.endpoint)
      .parsePersistentKeepalive(config.keepAlive)

    // 3. Montar a Configuração Final
    val vpnConfig = Config.Builder()
      .setInterface(interfaceBuilder.build())
      .addPeer(peerBuilder.build())
      .build()

    // 4. Ligar o VPN
    backend.setState(tunnel, Tunnel.State.UP, vpnConfig)

    // Inicia o serviço de ciclo de vida para monitorar se o app é fechado
    context.startService(Intent(context, VpnLifecycleService::class.java))

  }

  /**
   * Derruba a conexão do túnel atual.
   */
  override suspend fun disconnect() = withContext<Unit>(Dispatchers.IO) {
    backend.setState(tunnel, Tunnel.State.DOWN, null)
    context.stopService(Intent(context, VpnLifecycleService::class.java))
  }

  /**
   * Retorna o estado atual (UP, DOWN, TOGGLE).
   */
  override fun getTunnelState(): Tunnel.State {
    return backend.getState(tunnel)
  }
}