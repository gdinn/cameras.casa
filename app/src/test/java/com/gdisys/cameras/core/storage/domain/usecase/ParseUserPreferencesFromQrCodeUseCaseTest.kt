package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.model.SmbCredentials
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.model.VpnConfigDefaults
import com.gdisys.cameras.core.storage.domain.model.VpnConfigTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ParseUserPreferencesFromQrCodeUseCaseTest {

  private lateinit var useCase: ParseUserPreferencesFromQrCodeUseCase

  @Before
  fun setUp() {
    useCase = ParseUserPreferencesFromQrCodeUseCase()
  }

  private val validJson = """
    {
      "vpnConfigDefaults": {
        "iDns": "1.1.1.1",
        "iMtu": "1420",
        "pPuk": "public-key",
        "pAllowedips": "0.0.0.0/0",
        "pEndpoint": "vpn.example.com:51820",
        "pPersistentKeepAlive": "25"
      },
      "vpnConfigTokens": {
        "iPrk": "private-key",
        "iAddr": "10.0.0.2/32",
        "pPsk": "pre-shared-key"
      }
    }
  """.trimIndent()

  @Test
  fun `returns UserPreferences when json has valid credentials`() {
    val result = useCase(validJson)

    assertTrue(result.isSuccess)
    assertEquals(
      UserPreferences(
        vpnConfigDefaults = VpnConfigDefaults(
          iDns = "1.1.1.1",
          iMtu = "1420",
          pPuk = "public-key",
          pAllowedips = "0.0.0.0/0",
          pEndpoint = "vpn.example.com:51820",
          pPersistentKeepAlive = "25"
        ),
        vpnConfigTokens = VpnConfigTokens(
          iPrk = "private-key",
          iAddr = "10.0.0.2/32",
          pPsk = "pre-shared-key"
        )
      ),
      result.getOrNull()
    )
  }

  @Test
  fun `returns null when vpnConfigDefaults is invalid`() {
    val json = """
      {
        "vpnConfigDefaults": { "iDns": "1.1.1.1" },
        "vpnConfigTokens": {
          "iPrk": "private-key",
          "iAddr": "10.0.0.2/32",
          "pPsk": "pre-shared-key"
        }
      }
    """.trimIndent()

    val result = useCase(json)

    assertTrue(result.isSuccess)
    assertNull(result.getOrNull())
  }

  @Test
  fun `returns null when vpnConfigTokens is invalid`() {
    val json = """
      {
        "vpnConfigDefaults": {
          "iDns": "1.1.1.1",
          "iMtu": "1420",
          "pPuk": "public-key",
          "pAllowedips": "0.0.0.0/0",
          "pEndpoint": "vpn.example.com:51820",
          "pPersistentKeepAlive": "25"
        },
        "vpnConfigTokens": { "iPrk": "private-key" }
      }
    """.trimIndent()

    val result = useCase(json)

    assertTrue(result.isSuccess)
    assertNull(result.getOrNull())
  }

  @Test
  fun `returns null when both vpnConfigDefaults and vpnConfigTokens are missing`() {
    val result = useCase("{}")

    assertTrue(result.isSuccess)
    assertNull(result.getOrNull())
  }

  @Test
  fun `returns failure when json is malformed`() {
    val result = useCase("not a json")

    assertTrue(result.isFailure)
  }

  @Test
  fun `trims surrounding whitespace before parsing`() {
    val result = useCase("\n  $validJson  \n")

    assertTrue(result.isSuccess)
    assertEquals("private-key", result.getOrNull()?.vpnConfigTokens?.iPrk)
  }

  @Test
  fun `strips BOM character before parsing`() {
    val result = useCase("\uFEFF$validJson")

    assertTrue(result.isSuccess)
    assertEquals("private-key", result.getOrNull()?.vpnConfigTokens?.iPrk)
  }

  /** [validJson] with an `smbCredentials` object made of [fields]. */
  private fun withSmb(fields: String) = validJson.trimEnd().removeSuffix("}") + """,
      "smbCredentials": { $fields }
    }"""

  @Test
  fun `a QR without smbCredentials is a regular user`() {
    val result = useCase(validJson)

    assertNull(result.getOrNull()?.smbCredentials)
  }

  @Test
  fun `keeps complete smbCredentials`() {
    val result = useCase(withSmb(""""smbUser": "admin-a-7", "smbPassword": "secret""""))

    assertEquals(SmbCredentials(smbUser = "admin-a-7", smbPassword = "secret"), result.getOrNull()?.smbCredentials)
    assertEquals("private-key", result.getOrNull()?.vpnConfigTokens?.iPrk)
  }

  @Test
  fun `returns null when smbCredentials lacks the password`() {
    val result = useCase(withSmb(""""smbUser": "admin-a-7""""))

    assertTrue(result.isSuccess)
    assertNull(result.getOrNull())
  }

  @Test
  fun `returns null when smbCredentials has a blank user`() {
    val result = useCase(withSmb(""""smbUser": "  ", "smbPassword": "secret""""))

    assertTrue(result.isSuccess)
    assertNull(result.getOrNull())
  }

  @Test
  fun `returns null when smbCredentials is empty`() {
    val result = useCase(withSmb(""))

    assertTrue(result.isSuccess)
    assertNull(result.getOrNull())
  }
}
