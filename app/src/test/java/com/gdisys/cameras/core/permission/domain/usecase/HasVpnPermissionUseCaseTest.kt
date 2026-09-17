package com.gdisys.cameras.core.permission.domain.usecase

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
  fun `returns true when repository grants permission`() {
    every { vpnPermissionRepository.hasVpnPermission() } returns true

    assertTrue(useCase())
  }

  @Test
  fun `returns false when repository denies permission`() {
    every { vpnPermissionRepository.hasVpnPermission() } returns false

    assertFalse(useCase())
  }
}
