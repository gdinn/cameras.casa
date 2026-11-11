package com.gdisys.cameras.core.vpn.data

import app.cash.turbine.test
import com.gdisys.cameras.core.storage.domain.usecase.GetVpnConfigUseCase
import com.gdisys.cameras.core.vpn.domain.TunnelHolder
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import com.gdisys.cameras.core.vpn.domain.usecase.ConnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.DisconnectVpnUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val GRACE_MS = 1_500L

private val HOME = TunnelHolder.Screen("home")
private val HISTORY = TunnelHolder.Screen("history")
private val WORKER = TunnelHolder.Screen("worker")

@OptIn(ExperimentalCoroutinesApi::class)
class TunnelCoordinatorImplTest {

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

  /**
   * The tunnel behind the use cases: records the calls, how many ran at once, and moves the
   * repository's state like the real one. A connect can be held open with [holdConnects] and
   * released with [finishConnect], or made to fail with [connectFailure].
   */
  private inner class Tunnel {
    val state = MutableStateFlow(VpnTunnelState.DISCONNECTED)
    val calls = mutableListOf<String>()
    var maxInFlight = 0
      private set
    var connectFailure: Exception? = null
    private var gate: CompletableDeferred<Unit>? = null
    private var inFlight = 0

    val getVpnConfigUseCase = mockk<GetVpnConfigUseCase>()
    val connectVpnUseCase = mockk<ConnectVpnUseCase>()
    val disconnectVpnUseCase = mockk<DisconnectVpnUseCase>()
    val vpnRepository = mockk<VpnRepository>()

    init {
      coEvery { getVpnConfigUseCase() } returns Result.success(validConfig)
      coEvery { connectVpnUseCase(validConfig) } coAnswers {
        var failure: Exception? = null
        track("connect") {
          state.value = VpnTunnelState.CONNECTING
          gate?.await()
          failure = connectFailure
          state.value = if (failure == null) VpnTunnelState.CONNECTED else VpnTunnelState.DISCONNECTED
        }
        failure?.let { Result.failure(it) } ?: Result.success(Unit)
      }
      coEvery { disconnectVpnUseCase() } coAnswers {
        track("disconnect") { state.value = VpnTunnelState.DISCONNECTED }
        Result.success(Unit)
      }
      every { vpnRepository.vpnState } returns state
    }

    fun holdConnects() {
      gate = CompletableDeferred()
    }

    fun finishConnect() {
      gate?.complete(Unit)
      gate = null
    }

    private suspend fun track(call: String, body: suspend () -> Unit) {
      calls += call
      maxInFlight = maxOf(maxInFlight, ++inFlight)
      try {
        body()
      } finally {
        inFlight--
      }
    }
  }

  private val tunnel = Tunnel()

  private fun TestScope.coordinator() = TunnelCoordinatorImpl(
    tunnel.getVpnConfigUseCase,
    tunnel.connectVpnUseCase,
    tunnel.disconnectVpnUseCase,
    tunnel.vpnRepository,
    backgroundScope,
    GRACE_MS
  )

  @Test
  fun `the first holder brings the tunnel up`() = runTest {
    val coordinator = coordinator()

    coordinator.request(HOME)
    runCurrent()

    assertEquals(listOf("connect"), tunnel.calls)
    assertEquals(VpnTunnelState.CONNECTED, tunnel.state.value)
  }

  @Test
  fun `nothing is disconnected before anything was brought up`() = runTest {
    val coordinator = coordinator()

    coordinator.release(HOME)
    advanceTimeBy(GRACE_MS * 2)

    assertEquals(emptyList<String>(), tunnel.calls)
  }

  @Test
  fun `the tunnel goes down only once the grace period has passed`() = runTest {
    val coordinator = coordinator()
    coordinator.request(HOME)
    runCurrent()

    coordinator.release(HOME)
    advanceTimeBy(GRACE_MS - 1)
    assertEquals(listOf("connect"), tunnel.calls)

    advanceTimeBy(2)
    assertEquals(listOf("connect", "disconnect"), tunnel.calls)
  }

  @Test
  fun `a hand-over within the grace period keeps the tunnel up`() = runTest {
    val coordinator = coordinator()
    coordinator.request(HOME)
    runCurrent()

    // Home pauses before History resumes, as during a navigation transition.
    coordinator.release(HOME)
    advanceTimeBy(700)
    coordinator.request(HISTORY)
    advanceTimeBy(GRACE_MS * 2)

    assertEquals(listOf("connect"), tunnel.calls)
  }

  @Test
  fun `the tunnel stays up while another holder remains`() = runTest {
    val coordinator = coordinator()
    coordinator.request(HOME)
    coordinator.request(HISTORY)
    runCurrent()

    coordinator.release(HOME)
    advanceTimeBy(GRACE_MS * 2)
    assertEquals(listOf("connect"), tunnel.calls)

    coordinator.release(HISTORY)
    advanceTimeBy(GRACE_MS + 1)
    assertEquals(listOf("connect", "disconnect"), tunnel.calls)
  }

  @Test
  fun `a release during a connect is applied only after the connect returns`() = runTest {
    val coordinator = coordinator()
    tunnel.holdConnects()
    coordinator.request(HOME)
    runCurrent()

    coordinator.release(HOME)
    advanceTimeBy(GRACE_MS + 1)
    assertEquals(listOf("connect"), tunnel.calls)

    tunnel.finishConnect()
    runCurrent()

    assertEquals(listOf("connect", "disconnect"), tunnel.calls)
    assertEquals(1, tunnel.maxInFlight)
  }

  @Test
  fun `a quick release and request during a connect keeps the tunnel up`() = runTest {
    val coordinator = coordinator()
    tunnel.holdConnects()
    coordinator.request(HOME)
    runCurrent()

    coordinator.release(HOME)
    advanceTimeBy(GRACE_MS + 1)
    coordinator.request(HOME)
    runCurrent() // The new demand reaches the wish while the connect is still running.
    tunnel.finishConnect()
    advanceTimeBy(GRACE_MS * 2)

    // The intermediate "down" is dropped: the tunnel already is where the demand ended.
    assertEquals(listOf("connect"), tunnel.calls)
  }

  @Test
  fun `a failed connect is reported and the next request tries again`() = runTest {
    val coordinator = coordinator()
    tunnel.connectFailure = RuntimeException("boom")

    coordinator.connectFailures.test {
      coordinator.request(HOME)
      runCurrent()
      assertEquals("boom", awaitItem().message)

      // Back from Config within the grace period: the holder never left the coordinator's view.
      tunnel.connectFailure = null
      coordinator.release(HOME)
      advanceTimeBy(500)
      coordinator.request(HOME)
      runCurrent()

      assertEquals(listOf("connect", "connect"), tunnel.calls)
      assertEquals(VpnTunnelState.CONNECTED, tunnel.state.value)
      expectNoEvents()
    }
  }

  @Test
  fun `a failed config read is reported like a failed connect`() = runTest {
    coEvery { tunnel.getVpnConfigUseCase() } returns Result.failure(RuntimeException("no config"))
    val coordinator = coordinator()

    coordinator.connectFailures.test {
      coordinator.request(HOME)
      runCurrent()

      assertEquals("no config", awaitItem().message)
      assertEquals(emptyList<String>(), tunnel.calls)
    }
  }

  @Test
  fun `after a failed connect the release still disconnects`() = runTest {
    val coordinator = coordinator()
    tunnel.connectFailure = RuntimeException("boom")
    coordinator.request(HOME)
    runCurrent()

    coordinator.release(HOME)
    advanceTimeBy(GRACE_MS + 1)

    assertEquals(listOf("connect", "disconnect"), tunnel.calls)
  }

  @Test
  fun `holdIfUp never raises the tunnel`() = runTest {
    val coordinator = coordinator()

    assertFalse(coordinator.holdIfUp(WORKER))
    advanceTimeBy(GRACE_MS * 2)

    assertEquals(emptyList<String>(), tunnel.calls)
  }

  @Test
  fun `holdIfUp is refused while the tunnel is still coming up`() = runTest {
    val coordinator = coordinator()
    tunnel.holdConnects()
    coordinator.request(HOME)
    runCurrent()

    assertFalse(coordinator.holdIfUp(WORKER))
  }

  @Test
  fun `holdIfUp is refused once the tunnel went down`() = runTest {
    val coordinator = coordinator()
    coordinator.request(HOME)
    runCurrent()
    coordinator.release(HOME)
    advanceTimeBy(GRACE_MS + 1)

    assertFalse(coordinator.holdIfUp(WORKER))
  }

  @Test
  fun `a holder taken while the tunnel is up keeps it after the screen leaves`() = runTest {
    val coordinator = coordinator()
    coordinator.request(HOME)
    runCurrent()

    assertTrue(coordinator.holdIfUp(WORKER))
    coordinator.release(HOME)
    advanceTimeBy(GRACE_MS * 2)
    assertEquals(listOf("connect"), tunnel.calls)

    coordinator.release(WORKER)
    advanceTimeBy(GRACE_MS + 1)
    assertEquals(listOf("connect", "disconnect"), tunnel.calls)
  }

  @Test
  fun `holdIfUp within the grace period keeps the tunnel`() = runTest {
    val coordinator = coordinator()
    coordinator.request(HOME)
    runCurrent()
    coordinator.release(HOME)
    advanceTimeBy(GRACE_MS / 2)

    assertTrue(coordinator.holdIfUp(WORKER))
    advanceTimeBy(GRACE_MS * 2)

    assertEquals(listOf("connect"), tunnel.calls)
  }

  @Test
  fun `clear takes the tunnel down at once, whoever holds it`() = runTest {
    val coordinator = coordinator()
    coordinator.request(HOME)
    runCurrent()
    assertTrue(coordinator.holdIfUp(WORKER))

    coordinator.clear()
    runCurrent()

    assertEquals(listOf("connect", "disconnect"), tunnel.calls)
  }

  @Test
  fun `a request after clear brings the tunnel up again`() = runTest {
    val coordinator = coordinator()
    coordinator.request(HOME)
    runCurrent()
    coordinator.clear()
    runCurrent()

    coordinator.request(HOME)
    runCurrent()

    assertEquals(listOf("connect", "disconnect", "connect"), tunnel.calls)
  }

  @Test
  fun `holds tells whether a holder is registered`() = runTest {
    val coordinator = coordinator()
    val download = TunnelHolder.Download("work-1")
    coordinator.request(HOME)
    runCurrent()

    assertFalse(coordinator.holds(download))
    assertTrue(coordinator.holdIfUp(download))
    assertTrue(coordinator.holds(download))

    coordinator.release(download)
    assertFalse(coordinator.holds(download))
    assertTrue(coordinator.holds(HOME))
  }

  @Test
  fun `nothing is held after clear`() = runTest {
    val coordinator = coordinator()
    coordinator.request(HOME)
    runCurrent()

    coordinator.clear()

    assertFalse(coordinator.holds(HOME))
  }
}
