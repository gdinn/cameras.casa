package com.gdisys.cameras.core.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Conecta/desconecta a VPN de acordo com a visibilidade do app como um todo
 * (bloqueio de tela, app indo para segundo plano, etc.), e não da tela atual —
 * por isso usa o [ProcessLifecycleOwner] em vez do [androidx.lifecycle.compose.LocalLifecycleOwner]
 * padrão. Isso permite reaproveitar a mesma lógica em telas diferentes (Home,
 * Histórico) sem que a navegação entre elas dispare uma desconexão.
 */
@Composable
fun VpnSessionLifecycleEffect(
  onAppForegrounded: () -> Unit,
  onAppBackgrounded: () -> Unit
) {
  val processLifecycleOwner = ProcessLifecycleOwner.get()

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME, processLifecycleOwner) { onAppForegrounded() }
  LifecycleEventEffect(Lifecycle.Event.ON_PAUSE, processLifecycleOwner) { onAppBackgrounded() }
}
