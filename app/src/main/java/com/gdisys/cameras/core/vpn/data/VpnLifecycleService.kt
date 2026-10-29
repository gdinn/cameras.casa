package com.gdisys.cameras.core.vpn.data

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.gdisys.cameras.core.coroutines.IoDispatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VpnLifecycleService : Service() {

  @Inject
  lateinit var vpnTeardown: VpnTeardown

  @Inject
  @IoDispatcher
  lateinit var ioDispatcher: CoroutineDispatcher

  // Lazy because Hilt injects the dispatcher in onCreate, after the constructor runs.
  private val serviceScope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    return START_STICKY
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)

    // The app was swiped away from the recents list.
    serviceScope.launch {
      vpnTeardown()
      stopSelf()
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    // Makes sure the tunnel drops when the service is destroyed for any other reason too (the
    // system reclaiming it, say), not only through onTaskRemoved. After onTaskRemoved, the teardown
    // has already run and this call does nothing.
    serviceScope.launch {
      vpnTeardown()
    }
  }
}
