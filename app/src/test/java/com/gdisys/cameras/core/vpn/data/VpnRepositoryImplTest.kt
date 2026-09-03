package com.gdisys.cameras.core.vpn.data

import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.wireguard.android.backend.Tunnel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * VpnRepositoryImpl instancia GoBackend (biblioteca nativa do WireGuard) já no seu
 * bloco `init`, então a classe não pode ser instanciada em um unit test puro (JVM) sem
 * a lib nativa disponível. Aqui testamos apenas a lógica de mapeamento pura que o
 * arquivo expõe.
 */
class VpnRepositoryImplTest {

  @Test
  fun `maps UP to CONNECTED`() {
    assertEquals(VpnTunnelState.CONNECTED, Tunnel.State.UP.toVpnTunnelState())
  }

  @Test
  fun `maps DOWN to DISCONNECTED`() {
    assertEquals(VpnTunnelState.DISCONNECTED, Tunnel.State.DOWN.toVpnTunnelState())
  }

  @Test
  fun `maps TOGGLE to CONNECTING`() {
    assertEquals(VpnTunnelState.CONNECTING, Tunnel.State.TOGGLE.toVpnTunnelState())
  }
}
