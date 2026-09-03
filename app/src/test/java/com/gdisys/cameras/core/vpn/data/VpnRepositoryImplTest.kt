package com.gdisys.cameras.core.vpn.data

import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.BadConfigException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * VpnRepositoryImpl instancia GoBackend (biblioteca nativa do WireGuard) já no seu
 * bloco `init`, então a classe não pode ser instanciada em um unit test puro (JVM) sem
 * a lib nativa disponível. Aqui testamos apenas a lógica de mapeamento pura que o
 * arquivo expõe: a extensão `Tunnel.State.toVpnTunnelState()` e `VpnConfig.toWireGuardConfig()`.
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

  private val validConfig = VpnConfig(
    privateKey = "QaMQpp6+PaYhc0/2XzXLu6ogSvJ5wD2cTs7Jqu0ZMGs=",
    address = "10.8.0.2/32",
    dns = "1.1.1.1",
    publicKey = "1lxB+SksBUlA7FiIcR85GXkONLj4cFUplAndGzMjIfg=",
    preSharedKey = "LBPQpo46k4lQeqQT1vQ7y06TF4BJR/DqPJXbhDmfqcs=",
    endpoint = "vpn.example.com:51820",
    allowedIps = "0.0.0.0/0",
    keepAlive = "25",
    mtu = "1420"
  )

  @Test
  fun `toWireGuardConfig maps every VpnConfig field to the WireGuard Interface and Peer`() {
    val config = validConfig.toWireGuardConfig()

    assertEquals(
      "[Interface]\n" +
        "Address = 10.8.0.2/32\n" +
        "DNS = 1.1.1.1\n" +
        "MTU = 1420\n" +
        "PrivateKey = QaMQpp6+PaYhc0/2XzXLu6ogSvJ5wD2cTs7Jqu0ZMGs=\n" +
        "\n" +
        "[Peer]\n" +
        "AllowedIPs = 0.0.0.0/0\n" +
        "Endpoint = vpn.example.com:51820\n" +
        "PersistentKeepalive = 25\n" +
        "PreSharedKey = LBPQpo46k4lQeqQT1vQ7y06TF4BJR/DqPJXbhDmfqcs=\n" +
        "PublicKey = 1lxB+SksBUlA7FiIcR85GXkONLj4cFUplAndGzMjIfg=\n",
      config.toWgQuickString()
    )
  }

  @Test
  fun `toWireGuardConfig throws BadConfigException for a malformed private key`() {
    assertThrows(BadConfigException::class.java) {
      validConfig.copy(privateKey = "not-a-valid-key").toWireGuardConfig()
    }
  }

  @Test
  fun `toWireGuardConfig throws BadConfigException for a malformed endpoint`() {
    assertThrows(BadConfigException::class.java) {
      validConfig.copy(endpoint = "not a valid endpoint").toWireGuardConfig()
    }
  }

  @Test
  fun `toWireGuardConfig throws BadConfigException for a non-numeric mtu`() {
    assertThrows(BadConfigException::class.java) {
      validConfig.copy(mtu = "not-a-number").toWireGuardConfig()
    }
  }
}
