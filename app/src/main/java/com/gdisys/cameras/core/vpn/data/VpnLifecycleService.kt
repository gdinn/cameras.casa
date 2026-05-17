package com.gdisys.cameras.core.vpn.data

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VpnLifecycleService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject
    lateinit var vpnRepository: VpnRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // Quando o app é removido da lista de recentes (swipe away)
        serviceScope.launch {
            vpnRepository.disconnect()
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Opcional: garantir que a VPN caia se o serviço for destruído por outros motivos
    }
}
