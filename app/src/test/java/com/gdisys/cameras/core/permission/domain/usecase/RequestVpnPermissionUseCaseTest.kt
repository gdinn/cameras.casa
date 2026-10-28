package com.gdisys.cameras.core.permission.domain.usecase

import com.gdisys.cameras.core.permission.domain.VpnConsent
import com.gdisys.cameras.core.permission.domain.VpnPermissionRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class RequestVpnPermissionUseCaseTest {

  private val vpnPermissionRepository = mockk<VpnPermissionRepository>()
  private lateinit var useCase: RequestVpnPermissionUseCase

  @Before
  fun setUp() {
    useCase = RequestVpnPermissionUseCase(vpnPermissionRepository)
  }

  @Test
  fun `returns Required from the repository when consent is still needed`() {
    val consent = VpnConsent.Required(mockk())
    every { vpnPermissionRepository.getVpnConsent() } returns consent

    assertSame(consent, useCase())
  }

  @Test
  fun `returns Granted when consent was already given`() {
    every { vpnPermissionRepository.getVpnConsent() } returns VpnConsent.Granted

    assertSame(VpnConsent.Granted, useCase())
  }
}
