package com.gdisys.cameras.core.storage.domain.usecase

import app.cash.turbine.test
import com.gdisys.cameras.core.storage.domain.UserPreferencesRepository
import com.gdisys.cameras.core.storage.domain.model.SmbCredentials
import com.gdisys.cameras.core.storage.domain.model.UserPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveHasRecordingsAccessUseCaseTest {

  private val userPreferencesRepository = mockk<UserPreferencesRepository>()
  private val useCase = ObserveHasRecordingsAccessUseCase(userPreferencesRepository)

  private val withLogin = UserPreferences(smbCredentials = SmbCredentials(smbUser = "admin-a-7", smbPassword = "secret"))

  @Test
  fun `emits true for a complete Samba login`() = runTest {
    every { userPreferencesRepository.userPreferences } returns flowOf(withLogin)

    useCase().test {
      assertEquals(true, awaitItem())
      awaitComplete()
    }
  }

  @Test
  fun `emits true for a client peer's Samba login too`() = runTest {
    every { userPreferencesRepository.userPreferences } returns flowOf(
      UserPreferences(smbCredentials = SmbCredentials(smbUser = "client-c-5", smbPassword = "secret"))
    )

    useCase().test {
      assertEquals(true, awaitItem())
      awaitComplete()
    }
  }

  @Test
  fun `emits false without a Samba login or with an incomplete one`() = runTest {
    every { userPreferencesRepository.userPreferences } returns flowOf(
      UserPreferences(),
      UserPreferences(smbCredentials = SmbCredentials(smbUser = "admin-a-7"))
    )

    useCase().test {
      assertEquals(false, awaitItem())
      awaitComplete()
    }
  }

  @Test
  fun `follows a re-provisioning and skips repeated values`() = runTest {
    every { userPreferencesRepository.userPreferences } returns flowOf(UserPreferences(), withLogin, withLogin, UserPreferences())

    useCase().test {
      assertEquals(false, awaitItem())
      assertEquals(true, awaitItem())
      assertEquals(false, awaitItem())
      awaitComplete()
    }
  }
}
