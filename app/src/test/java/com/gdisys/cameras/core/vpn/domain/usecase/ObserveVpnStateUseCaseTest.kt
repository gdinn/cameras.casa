package com.gdisys.cameras.core.vpn.domain.usecase

import com.gdisys.cameras.core.vpn.domain.VpnRepository
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class ObserveVpnStateUseCaseTest {

  private val vpnRepository = mockk<VpnRepository>()
  private lateinit var useCase: ObserveVpnStateUseCase

  @Before
  fun setUp() {
    useCase = ObserveVpnStateUseCase(vpnRepository)
  }

  @Test
  fun `returns the same StateFlow instance exposed by the repository`() {
    val vpnState = MutableStateFlow(VpnTunnelState.CONNECTED)
    every { vpnRepository.vpnState } returns vpnState

    assertSame(vpnState, useCase())
  }
}
