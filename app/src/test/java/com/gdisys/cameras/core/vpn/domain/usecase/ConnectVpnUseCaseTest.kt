package com.gdisys.cameras.core.vpn.domain.usecase

import com.gdisys.cameras.core.vpn.domain.VpnLifecycleController
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConnectVpnUseCaseTest {

  private val vpnRepository = mockk<VpnRepository>()
  private val vpnLifecycleController = mockk<VpnLifecycleController>()
  private lateinit var useCase: ConnectVpnUseCase

  private val validConfig = VpnConfig(
    privateKey = "private-key",
    address = "10.0.0.2/32",
    dns = "1.1.1.1",
    publicKey = "public-key",
    preSharedKey = "pre-shared-key",
    endpoint = "vpn.example.com:51820",
    allowedIps = "0.0.0.0/0",
    keepAlive = "25",
    mtu = "1420"
  )

  @Before
  fun setUp() {
    useCase = ConnectVpnUseCase(vpnRepository, vpnLifecycleController)
  }

  @Test
  fun `returns failure when config is null`() = runTest {
    val result = useCase(null)

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
  }

  @Test
  fun `returns failure when config is invalid`() = runTest {
    val result = useCase(validConfig.copy(privateKey = ""))

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
  }

  @Test
  fun `connects and starts the lifecycle controller when config is valid`() = runTest {
    coEvery { vpnRepository.connect(validConfig) } returns Unit
    every { vpnLifecycleController.start() } returns Unit

    val result = useCase(validConfig)

    assertTrue(result.isSuccess)
    coVerify(exactly = 1) { vpnRepository.connect(validConfig) }
    verify(exactly = 1) { vpnLifecycleController.start() }
  }

  @Test
  fun `returns failure when the repository throws`() = runTest {
    val exception = RuntimeException("boom")
    coEvery { vpnRepository.connect(validConfig) } throws exception

    val result = useCase(validConfig)

    assertTrue(result.isFailure)
    assertEquals(exception, result.exceptionOrNull())
  }
}
