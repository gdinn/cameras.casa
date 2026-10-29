package com.gdisys.cameras.core.vpn.data

import com.gdisys.cameras.core.vpn.domain.VpnRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VpnTeardownTest {

  private val vpnRepository = mockk<VpnRepository>()
  private val teardown = VpnTeardown(vpnRepository)

  @Test
  fun `disconnects the tunnel`() = runTest {
    coEvery { vpnRepository.disconnect() } returns Unit

    teardown()

    coVerify(exactly = 1) { vpnRepository.disconnect() }
  }

  @Test
  fun `a second call after a successful disconnect does nothing`() = runTest {
    // onTaskRemoved stops the service, which then reaches onDestroy and calls it again.
    coEvery { vpnRepository.disconnect() } returns Unit

    teardown()
    teardown()

    coVerify(exactly = 1) { vpnRepository.disconnect() }
  }

  @Test
  fun `a failed disconnect does not throw and is tried again on the next call`() = runTest {
    coEvery { vpnRepository.disconnect() } throws RuntimeException("boom") andThen Unit

    teardown()
    teardown()
    teardown()

    coVerify(exactly = 2) { vpnRepository.disconnect() }
  }
}
