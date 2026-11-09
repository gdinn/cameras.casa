package com.gdisys.cameras.testing

import com.gdisys.cameras.core.vpn.domain.VpnLifecycleController
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.gdisys.cameras.core.vpn.domain.model.VpnConfig
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tunnel without WireGuard. `connect` goes to `CONNECTED`, or throws [connectFailure] when set;
 * `disconnect` goes to `DISCONNECTED`. The counters are atomic because the use cases call it off
 * the main thread.
 */
@Singleton
class FakeVpnRepository @Inject constructor() : VpnRepository {
  override val vpnState = MutableStateFlow(VpnTunnelState.DISCONNECTED)

  @Volatile var connectFailure: Exception? = null

  val connectCalls = AtomicInteger()
  val disconnectCalls = AtomicInteger()

  override suspend fun connect(config: VpnConfig) {
    connectCalls.incrementAndGet()
    connectFailure?.let { throw it }
    vpnState.value = VpnTunnelState.CONNECTED
  }

  override suspend fun disconnect() {
    disconnectCalls.incrementAndGet()
    vpnState.value = VpnTunnelState.DISCONNECTED
  }

  override fun getTunnelState(): VpnTunnelState = vpnState.value
}

/** Counts `start`/`stop`. The real `VpnLifecycleService` is never started through it. */
@Singleton
class FakeVpnLifecycleController @Inject constructor() : VpnLifecycleController {
  val startCalls = AtomicInteger()
  val stopCalls = AtomicInteger()

  override fun start() {
    startCalls.incrementAndGet()
  }

  override fun stop() {
    stopCalls.incrementAndGet()
  }
}
