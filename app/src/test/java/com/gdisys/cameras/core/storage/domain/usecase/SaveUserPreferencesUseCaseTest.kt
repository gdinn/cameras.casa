package com.gdisys.cameras.core.storage.domain.usecase

import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import com.gdisys.cameras.core.storage.domain.model.VpnConfigDefaults
import com.gdisys.cameras.core.storage.domain.model.VpnConfigTokens
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveUserPreferencesUseCaseTest {

  private val userPreferencesRepository = mockk<UserPreferencesRepository>()
  private lateinit var useCase: SaveUserPreferencesUseCase

  private val preferences = UserPreferences(
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
    useCase = SaveUserPreferencesUseCase(userPreferencesRepository)
  }

  @Test
  fun `returns success and delegates to the repository`() = runTest {
    coEvery { userPreferencesRepository.updateUserPreferences(preferences) } returns Unit

    val result = useCase(preferences)

    assertTrue(result.isSuccess)
    coVerify(exactly = 1) { userPreferencesRepository.updateUserPreferences(preferences) }
  }

  @Test
  fun `returns failure when the repository throws`() = runTest {
    val exception = RuntimeException("boom")
    coEvery { userPreferencesRepository.updateUserPreferences(preferences) } throws exception

    val result = useCase(preferences)

    assertTrue(result.isFailure)
    assertEquals(exception, result.exceptionOrNull())
  }
}
