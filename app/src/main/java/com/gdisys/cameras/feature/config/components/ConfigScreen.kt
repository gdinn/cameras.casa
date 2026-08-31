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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import com.gdisys.cameras.core.components.LoadingStorageScreen
import com.gdisys.cameras.core.components.QrCodeScreen
import com.gdisys.cameras.core.storage.domain.VpnDataUiState

@Composable
fun ConfigScreen(
  uiState: VpnDataUiState,
  acceptVpnPermission: () -> Unit,
  onNavigateToHome: () -> Unit,
  onQrCodeScanned: (String) -> Unit
) {
  Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
    var showScanner by remember { mutableStateOf(false) }

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
            Button(onClick = { showScanner = true }) {
              Text(text = stringResource(R.string.config_screen_reload_settings))
            }
          } else if (showScanner) {
            QrCodeScreen(
              onCodeScanned = { rawJson ->
                onQrCodeScanned(rawJson)
                showScanner = false
              }
            )
          } else {
            Button(onClick = { showScanner = true }) {
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
