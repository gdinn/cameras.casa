package com.gdisys.cameras.core.vpn.domain.usecase

import com.gdisys.cameras.core.vpn.domain.VpnLifecycleController
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DisconnectVpnUseCaseTest {

  private val vpnRepository = mockk<VpnRepository>()
  private val vpnLifecycleController = mockk<VpnLifecycleController>()
  private val testDispatcher = StandardTestDispatcher()
  private lateinit var useCase: DisconnectVpnUseCase

  @Before
  fun setUp() {
    useCase = DisconnectVpnUseCase(vpnRepository, vpnLifecycleController, testDispatcher)
  }

  @Test
  fun `disconnects and stops the lifecycle controller on success`() = runTest(testDispatcher) {
    coEvery { vpnRepository.disconnect() } returns Unit
    every { vpnLifecycleController.stop() } returns Unit

    val result = useCase()

    assertTrue(result.isSuccess)
    coVerify(exactly = 1) { vpnRepository.disconnect() }
    verify(exactly = 1) { vpnLifecycleController.stop() }
  }

  @Test
  fun `returns failure when the repository throws`() = runTest(testDispatcher) {
    val exception = RuntimeException("boom")
    coEvery { vpnRepository.disconnect() } throws exception

    val result = useCase()

    assertTrue(result.isFailure)
    assertEquals(exception, result.exceptionOrNull())
  }

  @Test
  fun `disconnects on the injected dispatcher`() = runTest {
    val injectedDispatcher = StandardTestDispatcher(testScheduler)
    var disconnectDispatcher: ContinuationInterceptor? = null
    coEvery { vpnRepository.disconnect() } coAnswers {
      disconnectDispatcher = currentCoroutineContext()[ContinuationInterceptor]
    }
    every { vpnLifecycleController.stop() } returns Unit

    DisconnectVpnUseCase(vpnRepository, vpnLifecycleController, injectedDispatcher)()

    assertSame(injectedDispatcher, disconnectDispatcher)
  }
}
