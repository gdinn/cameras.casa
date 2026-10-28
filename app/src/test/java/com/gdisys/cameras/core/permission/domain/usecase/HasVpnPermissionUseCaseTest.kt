package com.gdisys.cameras.core.permission.domain.usecase

import com.gdisys.cameras.core.permission.domain.VpnConsent
import com.gdisys.cameras.core.permission.domain.VpnPermissionRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HasVpnPermissionUseCaseTest {

  private val vpnPermissionRepository = mockk<VpnPermissionRepository>()
  private lateinit var useCase: HasVpnPermissionUseCase

  @Before
  fun setUp() {
    useCase = HasVpnPermissionUseCase(vpnPermissionRepository)
  }

  @Test
  fun `returns true when consent is granted`() {
    every { vpnPermissionRepository.getVpnConsent() } returns VpnConsent.Granted

    assertTrue(useCase())
  }

  @Test
  fun `returns false when consent is still required`() {
    every { vpnPermissionRepository.getVpnConsent() } returns VpnConsent.Required(mockk())

    assertFalse(useCase())
  }
}
