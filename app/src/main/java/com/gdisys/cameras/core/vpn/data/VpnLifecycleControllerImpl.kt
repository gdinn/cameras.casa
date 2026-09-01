package com.gdisys.cameras.core.vpn.data

import android.content.Context
import android.content.Intent
import com.gdisys.cameras.core.vpn.domain.VpnLifecycleController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnLifecycleControllerImpl @Inject constructor(
  @ApplicationContext private val context: Context
) : VpnLifecycleController {

  override fun start() {
    context.startService(Intent(context, VpnLifecycleService::class.java))
  }

  override fun stop() {
    context.stopService(Intent(context, VpnLifecycleService::class.java))
  }
}
