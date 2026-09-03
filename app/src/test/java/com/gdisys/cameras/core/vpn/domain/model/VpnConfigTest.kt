package com.gdisys.cameras.core.vpn.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnConfigTest {

  private val validConfig = VpnConfig(
    privateKey = "private-key",
    address = "10.0.0.2/32",
    dns = "1.1.1.1",
    publicKey = "public-key",
    preSharedKey = "pre-shared-key",
    endpoint = "vpn.example.com:51820",
    allowedIps = "0.0.0.0/0",
    keepAlive = "25",
    mtu = "1420"
  )

  @Test
  fun `isValid true when all fields are non-blank`() {
    assertTrue(validConfig.isValid())
  }

  @Test
  fun `isValid false when privateKey is blank`() {
    assertFalse(validConfig.copy(privateKey = "").isValid())
  }

  @Test
  fun `isValid false when address is blank`() {
    assertFalse(validConfig.copy(address = "   ").isValid())
  }

  @Test
  fun `isValid false when dns is blank`() {
    assertFalse(validConfig.copy(dns = "").isValid())
  }

  @Test
  fun `isValid false when publicKey is blank`() {
    assertFalse(validConfig.copy(publicKey = "").isValid())
  }

  @Test
  fun `isValid false when preSharedKey is blank`() {
    assertFalse(validConfig.copy(preSharedKey = "").isValid())
  }

  @Test
  fun `isValid false when endpoint is blank`() {
    assertFalse(validConfig.copy(endpoint = "").isValid())
  }

  @Test
  fun `isValid false when allowedIps is blank`() {
    assertFalse(validConfig.copy(allowedIps = "").isValid())
  }

  @Test
  fun `isValid false when keepAlive is blank`() {
    assertFalse(validConfig.copy(keepAlive = "").isValid())
  }

  @Test
  fun `isValid false when mtu is blank`() {
    assertFalse(validConfig.copy(mtu = "").isValid())
  }
}
