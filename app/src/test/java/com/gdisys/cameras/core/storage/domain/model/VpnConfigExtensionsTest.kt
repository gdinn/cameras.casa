package com.gdisys.cameras.core.storage.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnConfigExtensionsTest {

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

  @Test
  fun `tokens isValid true when all fields present`() {
    assertTrue(validTokens.isValid())
    assertFalse(validTokens.isInvalid())
  }

  @Test
  fun `tokens isValid false when iPrk is null`() {
    val tokens = validTokens.copy(iPrk = null)
    assertFalse(tokens.isValid())
    assertTrue(tokens.isInvalid())
  }

  @Test
  fun `tokens isValid false when iAddr is null`() {
    val tokens = validTokens.copy(iAddr = null)
    assertFalse(tokens.isValid())
    assertTrue(tokens.isInvalid())
  }

  @Test
  fun `tokens isValid false when pPsk is null`() {
    val tokens = validTokens.copy(pPsk = null)
    assertFalse(tokens.isValid())
    assertTrue(tokens.isInvalid())
  }

  @Test
  fun `tokens isValid false when all fields are null`() {
    val tokens = VpnConfigTokens()
    assertFalse(tokens.isValid())
    assertTrue(tokens.isInvalid())
  }

  @Test
  fun `defaults isValid true when all fields present`() {
    assertTrue(validDefaults.isValid())
    assertFalse(validDefaults.isInvalid())
  }

  @Test
  fun `defaults isValid false when iDns is null`() {
    val defaults = validDefaults.copy(iDns = null)
    assertFalse(defaults.isValid())
    assertTrue(defaults.isInvalid())
  }

  @Test
  fun `defaults isValid false when iMtu is null`() {
    val defaults = validDefaults.copy(iMtu = null)
    assertFalse(defaults.isValid())
    assertTrue(defaults.isInvalid())
  }

  @Test
  fun `defaults isValid false when pPuk is null`() {
    val defaults = validDefaults.copy(pPuk = null)
    assertFalse(defaults.isValid())
    assertTrue(defaults.isInvalid())
  }

  @Test
  fun `defaults isValid false when pAllowedips is null`() {
    val defaults = validDefaults.copy(pAllowedips = null)
    assertFalse(defaults.isValid())
    assertTrue(defaults.isInvalid())
  }

  @Test
  fun `defaults isValid false when pEndpoint is null`() {
    val defaults = validDefaults.copy(pEndpoint = null)
    assertFalse(defaults.isValid())
    assertTrue(defaults.isInvalid())
  }

  @Test
  fun `defaults isValid false when pPersistentKeepAlive is null`() {
    val defaults = validDefaults.copy(pPersistentKeepAlive = null)
    assertFalse(defaults.isValid())
    assertTrue(defaults.isInvalid())
  }

  @Test
  fun `defaults isValid false when all fields are null`() {
    val defaults = VpnConfigDefaults()
    assertFalse(defaults.isValid())
    assertTrue(defaults.isInvalid())
  }
}
