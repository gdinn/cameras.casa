package com.gdisys.cameras

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import com.wireguard.android.backend.Tunnel
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CamerasApp : Application() {

  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var wasConnected = false

  @Inject
  lateinit var vpnRepository: VpnRepository

  override fun onCreate() {
    super.onCreate()

    ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {

      override fun onStop(owner: LifecycleOwner) {
        // App indo para o segundo plano
        wasConnected = vpnRepository.getTunnelState() == Tunnel.State.UP
        if (wasConnected) {
          applicationScope.launch {
            vpnRepository.disconnect()
          }
        }
      }
    })
  }
}
