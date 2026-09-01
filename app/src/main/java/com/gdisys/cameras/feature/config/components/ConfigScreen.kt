package com.gdisys.cameras.feature.config.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import com.gdisys.cameras.core.components.LoadingStorageScreen
import com.gdisys.cameras.core.storage.domain.VpnDataUiState

@Composable
fun ConfigScreen(
  uiState: VpnDataUiState,
  showScanner: Boolean,
  onShowScanner: () -> Unit,
  acceptVpnPermission: () -> Unit,
  onNavigateToHome: () -> Unit,
  qrCodeScanner: @Composable () -> Unit
) {
  Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {

      when (uiState) {
        is VpnDataUiState.Loading -> {
          LoadingStorageScreen()
        }

        is VpnDataUiState.Success -> {
          if (!uiState.vpnConfigTokensEmpty && !showScanner) {
            Text(text = stringResource(R.string.config_screen_settings_loaded))
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onShowScanner) {
              Text(text = stringResource(R.string.config_screen_reload_settings))
            }
          } else if (showScanner) {
            qrCodeScanner()
          } else {
            Button(onClick = onShowScanner) {
              Text(text = stringResource(R.string.config_screen_load_settings))
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          Button(onClick = acceptVpnPermission) {
            Text(text = stringResource(R.string.config_screen_accept_vpn_permission))
          }
          if (!uiState.vpnConfigTokensEmpty && !showScanner) {
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { onNavigateToHome() }) {
              Text(text = stringResource(R.string.config_screen_navigate_to_home))
            }
          }
        }
      }
    }
  }
}
