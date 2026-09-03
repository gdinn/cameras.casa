package com.gdisys.cameras.core.storage.domain.usecase

import app.cash.turbine.test
import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import com.gdisys.cameras.core.storage.domain.VpnCredentialsStatus
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.model.VpnConfigDefaults
import com.gdisys.cameras.core.storage.domain.model.VpnConfigTokens
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetVpnConfigStatusUseCaseTest {

  private val userPreferencesRepository = mockk<UserPreferencesRepository>()
  private lateinit var useCase: GetVpnConfigStatusUseCase

  private val validDefaults = VpnConfigDefaults(
    iDns = "1.1.1.1",
    iMtu = "1420",
    pPuk = "public-key",
    pAllowedips = "0.0.0.0/0",
    pEndpoint = "vpn.example.com:51820",
    pPersistentKeepAlive = "25"
  )

  private val validTokens = VpnConfigTokens(
    iPrk = "private-key",
    iAddr = "10.0.0.2/32",
    pPsk = "pre-shared-key"
  )

  @Before
  fun setUp() {
    useCase = GetVpnConfigStatusUseCase(userPreferencesRepository)
  }

  @Test
  fun `emits Loaded true when tokens and defaults are valid`() = runTest {
    every { userPreferencesRepository.userPreferences } returns flowOf(
      UserPreferences(vpnConfigDefaults = validDefaults, vpnConfigTokens = validTokens)
    )

    useCase().test {
      assertEquals(VpnCredentialsStatus.Loaded(hasValidCredentials = true), awaitItem())
      awaitComplete()
    }
  }

  @Test
  fun `emits Loaded false when tokens are invalid`() = runTest {
    every { userPreferencesRepository.userPreferences } returns flowOf(
      UserPreferences(vpnConfigDefaults = validDefaults, vpnConfigTokens = validTokens.copy(iPrk = null))
    )

    useCase().test {
      assertEquals(VpnCredentialsStatus.Loaded(hasValidCredentials = false), awaitItem())
      awaitComplete()
    }
  }

  @Test
  fun `emits Loaded false when defaults are invalid`() = runTest {
    every { userPreferencesRepository.userPreferences } returns flowOf(
      UserPreferences(vpnConfigDefaults = validDefaults.copy(pPuk = null), vpnConfigTokens = validTokens)
    )

    useCase().test {
      assertEquals(VpnCredentialsStatus.Loaded(hasValidCredentials = false), awaitItem())
      awaitComplete()
    }
  }

  @Test
  fun `emits Loaded false when tokens and defaults are both null`() = runTest {
    every { userPreferencesRepository.userPreferences } returns flowOf(UserPreferences())

    useCase().test {
      assertEquals(VpnCredentialsStatus.Loaded(hasValidCredentials = false), awaitItem())
      awaitComplete()
    }
  }
}
