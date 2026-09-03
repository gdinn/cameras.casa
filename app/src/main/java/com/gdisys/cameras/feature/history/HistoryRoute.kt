package com.gdisys.cameras.feature.history

import androidx.compose.runtime.Composable
import com.gdisys.cameras.core.components.VpnSessionLifecycleEffect
import com.gdisys.cameras.feature.history.components.HistoryScreen

@Composable
fun HistoryRoute(
  viewModel: HistoryViewModel
) {
  VpnSessionLifecycleEffect(
    onAppForegrounded = viewModel::connectVpn,
    onAppBackgrounded = viewModel::disconnectVpn
  )

  HistoryScreen()
}
