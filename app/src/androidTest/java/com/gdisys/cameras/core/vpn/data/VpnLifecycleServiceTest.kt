package com.gdisys.cameras.core.vpn.data

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.gdisys.cameras.testing.FakeVpnRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * The real [VpnLifecycleService], injected by Hilt, over the [FakeVpnRepository] that
 * `TestVpnModule` installs. Proves the wiring the JVM test of [VpnTeardown] cannot: stopping the
 * service drops the tunnel.
 *
 * `onTaskRemoved` (swiping the app away from recents) has no API to trigger it from a test, so it
 * stays a manual check.
 */
@HiltAndroidTest
class VpnLifecycleServiceTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @Inject
  lateinit var vpnRepository: FakeVpnRepository

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val serviceIntent = Intent(context, VpnLifecycleService::class.java)

  @Before
  fun injectDependencies() {
    hiltRule.inject()
  }

  @Test
  fun stoppingTheServiceDropsTheTunnel() {
    vpnRepository.vpnState.value = VpnTunnelState.CONNECTED

    context.startService(serviceIntent)
    context.stopService(serviceIntent)

    // onDestroy disconnects in the service's own scope, off the main thread.
    runBlocking {
      withTimeout(5_000) { vpnRepository.vpnState.first { it == VpnTunnelState.DISCONNECTED } }
    }
    assertEquals(1, vpnRepository.disconnectCalls.get())
  }
}
