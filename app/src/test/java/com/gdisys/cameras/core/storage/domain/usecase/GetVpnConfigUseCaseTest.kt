package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.model.VpnConfigDefaults
import com.gdisys.cameras.core.storage.domain.model.VpnConfigTokens
import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetVpnConfigUseCaseTest {

  private val userPreferencesRepository = mockk<UserPreferencesRepository>()
  private lateinit var useCase: GetVpnConfigUseCase

  private val validPreferences = UserPreferences(
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
  )

  @Before
  fun setUp() {
    useCase = GetVpnConfigUseCase(userPreferencesRepository)
  }

  @Test
  fun `returns success with VpnConfig when preferences are valid`() = runTest {
    every { userPreferencesRepository.userPreferences } returns flowOf(validPreferences)

    val result = useCase()

    assertTrue(result.isSuccess)
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
      result.getOrNull()
    )
  }

  @Test
  fun `returns success with null when preferences are incomplete`() = runTest {
    every { userPreferencesRepository.userPreferences } returns flowOf(UserPreferences())

    val result = useCase()

    assertTrue(result.isSuccess)
    assertNull(result.getOrNull())
  }

  @Test
  fun `returns failure when the flow throws`() = runTest {
    val exception = RuntimeException("boom")
    every { userPreferencesRepository.userPreferences } returns flow { throw exception }

    val result = useCase()

    assertTrue(result.isFailure)
    assertEquals(exception, result.exceptionOrNull())
  }
}
