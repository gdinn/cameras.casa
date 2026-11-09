package com.gdisys.cameras.core.vpn.data

import android.util.Log
import com.gdisys.cameras.core.DEBUG_TAG
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Drops the tunnel on behalf of [VpnLifecycleService], kept out of the service so it can be tested
 * on the JVM. Unscoped, so each service instance gets its own.
 *
 * - **Idempotent:** `onTaskRemoved` stops the service, which then reaches `onDestroy` too. Once a
 *   disconnect succeeds, later calls do nothing; after a failure, the next call tries again.
 * - **Never throws:** it runs in a service scope with no exception handler, where an exception
 *   would crash the app. A failure is logged instead.
 */
class VpnTeardown @Inject constructor(
  private val vpnRepository: VpnRepository
) {
  private val mutex = Mutex()
  private var tornDown = false

  suspend operator fun invoke() = mutex.withLock {
    if (tornDown) return@withLock
    try {
      vpnRepository.disconnect()
      tornDown = true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(DEBUG_TAG, "Failed to drop the tunnel", e)
    }
  }
}
