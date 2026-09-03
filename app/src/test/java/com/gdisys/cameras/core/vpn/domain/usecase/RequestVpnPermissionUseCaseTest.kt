package com.gdisys.cameras.core.vpn.domain.usecase

import android.content.Intent
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class RequestVpnPermissionUseCaseTest {

  private val vpnRepository = mockk<VpnRepository>()
  private lateinit var useCase: RequestVpnPermissionUseCase

  @Before
  fun setUp() {
    useCase = RequestVpnPermissionUseCase(vpnRepository)
  }

  @Test
  fun `returns the intent from the repository when permission is required`() {
    val intent = mockk<Intent>()
    every { vpnRepository.getVpnPermissionIntent() } returns intent

    assertSame(intent, useCase())
  }

  @Test
  fun `returns null when permission is already granted`() {
    every { vpnRepository.getVpnPermissionIntent() } returns null

    assertNull(useCase())
  }
}
