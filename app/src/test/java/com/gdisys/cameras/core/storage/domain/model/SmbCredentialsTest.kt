package com.gdisys.cameras.core.storage.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbCredentialsTest {

  @Test
  fun `is valid with a user and a password`() {
    assertTrue(SmbCredentials(smbUser = "admin-a-7", smbPassword = "secret").isValid())
  }

  @Test
  fun `is invalid when a field is missing or blank`() {
    assertFalse(SmbCredentials().isValid())
    assertFalse(SmbCredentials(smbUser = "admin-a-7").isValid())
    assertFalse(SmbCredentials(smbPassword = "secret").isValid())
    assertFalse(SmbCredentials(smbUser = "", smbPassword = "secret").isValid())
    assertFalse(SmbCredentials(smbUser = "admin-a-7", smbPassword = " ").isValid())
  }

  @Test
  fun `toString never prints the password`() {
    assertEquals(
      "SmbCredentials(smbUser=admin-a-7, smbPassword=***)",
      SmbCredentials(smbUser = "admin-a-7", smbPassword = "secret").toString()
    )
    assertEquals("SmbCredentials(smbUser=null, smbPassword=null)", SmbCredentials().toString())
  }
}
