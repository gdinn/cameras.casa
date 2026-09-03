package com.gdisys.cameras.core.vpn.domain

import com.gdisys.cameras.core.storage.domain.usecase.GetVpnConfigUseCase
import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import com.gdisys.cameras.core.vpn.domain.usecase.ConnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.DisconnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.ObserveVpnStateUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VpnSessionCoordinatorTest {

  private val vpnState = MutableStateFlow(VpnTunnelState.DISCONNECTED)

  private val observeVpnStateUseCase = mockk<ObserveVpnStateUseCase>()
  private val connectVpnUseCase = mockk<ConnectVpnUseCase>()
  private val disconnectVpnUseCase = mockk<DisconnectVpnUseCase>()
  private val getVpnConfigUseCase = mockk<GetVpnConfigUseCase>()

  private lateinit var coordinator: VpnSessionCoordinator

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
    every { observeVpnStateUseCase() } returns vpnState
    coordinator = VpnSessionCoordinator(
      observeVpnStateUseCase,
      connectVpnUseCase,
      disconnectVpnUseCase,
      getVpnConfigUseCase
    )
  }

  @Test
  fun `vpnState exposes the underlying vpn state`() {
    assertEquals(vpnState, coordinator.vpnState)
  }

  @Test
  fun `connect does nothing when the vpn is already connected`() = runTest {
    vpnState.value = VpnTunnelState.CONNECTED

    val result = coordinator.connect()

    assertTrue(result.isSuccess)
    coVerify(exactly = 0) { getVpnConfigUseCase() }
    coVerify(exactly = 0) { connectVpnUseCase(any()) }
  }

  @Test
  fun `connect does nothing when a connection attempt is already in progress`() = runTest {
    vpnState.value = VpnTunnelState.CONNECTING

    val result = coordinator.connect()

    assertTrue(result.isSuccess)
    coVerify(exactly = 0) { getVpnConfigUseCase() }
    coVerify(exactly = 0) { connectVpnUseCase(any()) }
  }

  @Test
  fun `connect fetches the config and connects when not already connected`() = runTest {
    coEvery { getVpnConfigUseCase() } returns Result.success(validConfig)
    coEvery { connectVpnUseCase(validConfig) } returns Result.success(Unit)

    val result = coordinator.connect()

    assertTrue(result.isSuccess)
    coVerify(exactly = 1) { connectVpnUseCase(validConfig) }
  }

  @Test
  fun `connect fails without connecting when reading the config fails`() = runTest {
    val error = RuntimeException("boom")
    coEvery { getVpnConfigUseCase() } returns Result.failure(error)

    val result = coordinator.connect()

    assertEquals(error, result.exceptionOrNull())
    coVerify(exactly = 0) { connectVpnUseCase(any()) }
  }

  @Test
  fun `connect propagates a failure from connectVpnUseCase`() = runTest {
    val error = RuntimeException("boom")
    coEvery { getVpnConfigUseCase() } returns Result.success(validConfig)
    coEvery { connectVpnUseCase(validConfig) } returns Result.failure(error)

    val result = coordinator.connect()

    assertEquals(error, result.exceptionOrNull())
  }

  @Test
  fun `disconnect delegates to the use case`() = runTest {
    coEvery { disconnectVpnUseCase() } returns Result.success(Unit)

    val result = coordinator.disconnect()

    assertTrue(result.isSuccess)
    coVerify(exactly = 1) { disconnectVpnUseCase() }
  }
}
