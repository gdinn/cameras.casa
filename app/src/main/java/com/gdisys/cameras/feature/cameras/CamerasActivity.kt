package com.gdisys.cameras.feature.cameras

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.gdisys.cameras.feature.cameras.components.CamerasLoadingScreen
import com.gdisys.cameras.ui.theme.CamerasTheme
import com.wireguard.android.backend.Tunnel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CamerasActivity : ComponentActivity() {

  private val viewModel: HomeViewModel by viewModels()

  // Colocar isso aqui lá... MainActivity de repente
  private val processObserver = object : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
      // Conecta ao voltar para foreground
      viewModel.connectVpn()
    }

    override fun onStop(owner: LifecycleOwner) {
      // Desconecta ao ir para background
      viewModel.disconnectVpn()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)

    setContent {
      CamerasTheme {
        /*
        Nota para o futuro:
        DashboardScreen está OK
        CamerasLoadingScreen está OK

        Desafio: Ver como integrar vpnState, isConnecting e vpnReady para a navegação
          -> Não tem viewModel, não tem activity e nem fragment
          -> Como é o ciclo de vida no composable nesse caso?
         */

        val vpnState by viewModel.vpnState.collectAsState()
        val isConnecting by viewModel.isConnecting.collectAsState()
        val vpnReady = vpnState == Tunnel.State.UP && !isConnecting

        if (vpnReady) {
          HomeRoute()
        } else {
          CamerasLoadingScreen()
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
  }
}