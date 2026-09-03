package com.gdisys.cameras.core.storage.domain

import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.model.VpnConfigDefaults
import com.gdisys.cameras.core.storage.domain.model.VpnConfigTokens
import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserPreferencesExtensionsTest {

  private val validTokens = VpnConfigTokens(
    iPrk = "private-key",
    iAddr = "10.0.0.2/32",
    pPsk = "pre-shared-key"
  )

  private val validDefaults = VpnConfigDefaults(
    iDns = "1.1.1.1",
    iMtu = "1420",
    pPuk = "public-key",
    pAllowedips = "0.0.0.0/0",
    pEndpoint = "vpn.example.com:51820",
    pPersistentKeepAlive = "25"
  )

  private val validPreferences = UserPreferences(
    vpnConfigDefaults = validDefaults,
    vpnConfigTokens = validTokens
  )

  @Test
  fun `maps to VpnConfig when all fields are present`() {
    val config = validPreferences.toVpnConfigOrNull()

    assertEquals(
      VpnConfig(
        privateKey = "private-key",
        publicKey = "public-key",
        address = "10.0.0.2/32",
        endpoint = "vpn.example.com:51820",
        allowedIps = "0.0.0.0/0",
        dns = "1.1.1.1",
        preSharedKey = "pre-shared-key",
        keepAlive = "25",
        mtu = "1420"
      ),
      config
    )
  }

  @Test
  fun `returns null when vpnConfigTokens is null`() {
    assertNull(validPreferences.copy(vpnConfigTokens = null).toVpnConfigOrNull())
  }

  @Test
  fun `returns null when vpnConfigDefaults is null`() {
    assertNull(validPreferences.copy(vpnConfigDefaults = null).toVpnConfigOrNull())
  }

  @Test
  fun `returns null when iPrk is null`() {
    val prefs = validPreferences.copy(vpnConfigTokens = validTokens.copy(iPrk = null))
    assertNull(prefs.toVpnConfigOrNull())
  }

  @Test
  fun `returns null when pPuk is null`() {
    val prefs = validPreferences.copy(vpnConfigDefaults = validDefaults.copy(pPuk = null))
    assertNull(prefs.toVpnConfigOrNull())
  }

  @Test
  fun `returns null when iAddr is null`() {
    val prefs = validPreferences.copy(vpnConfigTokens = validTokens.copy(iAddr = null))
    assertNull(prefs.toVpnConfigOrNull())
  }

  @Test
  fun `returns null when iDns is null`() {
    val prefs = validPreferences.copy(vpnConfigDefaults = validDefaults.copy(iDns = null))
    assertNull(prefs.toVpnConfigOrNull())
  }

  @Test
  fun `returns null when pPsk is null`() {
    val prefs = validPreferences.copy(vpnConfigTokens = validTokens.copy(pPsk = null))
    assertNull(prefs.toVpnConfigOrNull())
  }

  @Test
  fun `returns null when pPersistentKeepAlive is null`() {
    val prefs = validPreferences.copy(vpnConfigDefaults = validDefaults.copy(pPersistentKeepAlive = null))
    assertNull(prefs.toVpnConfigOrNull())
  }

  @Test
  fun `returns null when iMtu is null`() {
    val prefs = validPreferences.copy(vpnConfigDefaults = validDefaults.copy(iMtu = null))
    assertNull(prefs.toVpnConfigOrNull())
  }

  @Test
  fun `returns null when pEndpoint is null`() {
    val prefs = validPreferences.copy(vpnConfigDefaults = validDefaults.copy(pEndpoint = null))
    assertNull(prefs.toVpnConfigOrNull())
  }

  @Test
  fun `returns null when pAllowedips is null`() {
    val prefs = validPreferences.copy(vpnConfigDefaults = validDefaults.copy(pAllowedips = null))
    assertNull(prefs.toVpnConfigOrNull())
  }
}
