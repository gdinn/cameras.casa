package com.gdisys.cameras.core.vpn.domain.usecase

import com.gdisys.cameras.core.vpn.domain.VpnLifecycleController
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DisconnectVpnUseCaseTest {

  private val vpnRepository = mockk<VpnRepository>()
  private val vpnLifecycleController = mockk<VpnLifecycleController>()
  private lateinit var useCase: DisconnectVpnUseCase

  @Before
  fun setUp() {
    useCase = DisconnectVpnUseCase(vpnRepository, vpnLifecycleController)
  }

  @Test
  fun `disconnects and stops the lifecycle controller on success`() = runTest {
    coEvery { vpnRepository.disconnect() } returns Unit
    every { vpnLifecycleController.stop() } returns Unit

    val result = useCase()

    assertTrue(result.isSuccess)
    coVerify(exactly = 1) { vpnRepository.disconnect() }
    verify(exactly = 1) { vpnLifecycleController.stop() }
  }

  @Test
  fun `returns failure when the repository throws`() = runTest {
    val exception = RuntimeException("boom")
    coEvery { vpnRepository.disconnect() } throws exception

    val result = useCase()

    assertTrue(result.isFailure)
    assertEquals(exception, result.exceptionOrNull())
  }
}
