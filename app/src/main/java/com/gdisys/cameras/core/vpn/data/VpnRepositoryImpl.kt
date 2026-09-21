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
    Log.d(DEBUG_TAG, "Tunnel state changed to: $newState")
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
 * Pure `VpnConfig` -> WireGuard `Config` mapping, extracted out of [VpnRepositoryImpl.connect] so
 * it can be unit-tested without instantiating `GoBackend`, a native library that does not run on a
 * plain JVM.
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

  // Keeping a single Backend instance for the app's lifetime is strongly recommended.
  private val backend: Backend by lazy { GoBackend(context) }

  private val _vpnState = MutableStateFlow(VpnTunnelState.DISCONNECTED)
  override val vpnState: StateFlow<VpnTunnelState> = _vpnState.asStateFlow()

  private val tunnel = AppTunnel { newState ->
    _vpnState.value = newState.toVpnTunnelState()
  }

  init {
    // Seed the initial state from the backend, which GoBackend lets us query directly.
    _vpnState.value = backend.getState(tunnel).toVpnTunnelState()
  }

  /**
   * Brings the tunnel up with the given WireGuard server parameters.
   */
  override suspend fun connect(
    config: VpnConfig
  ) = withContext<Unit>(Dispatchers.IO) {
    _vpnState.value = VpnTunnelState.CONNECTING
    backend.setState(tunnel, Tunnel.State.UP, config.toWireGuardConfig())
  }

  /**
   * Drops the current tunnel connection.
   */
  override suspend fun disconnect() = withContext<Unit>(Dispatchers.IO) {
    backend.setState(tunnel, Tunnel.State.DOWN, null)
  }

  /**
   * Returns the current state (CONNECTED, DISCONNECTED or CONNECTING).
   */
  override fun getTunnelState(): VpnTunnelState {
    return backend.getState(tunnel).toVpnTunnelState()
  }

  override fun getVpnPermissionIntent(): Intent? = VpnService.prepare(context)
}