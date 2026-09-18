package com.gdisys.cameras.feature.config.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import com.gdisys.cameras.core.components.LoadingScreen
import com.gdisys.cameras.feature.config.ConfigButtonState
import com.gdisys.cameras.feature.config.ConfigUiState

@Composable
fun ConfigScreen(
  uiState: ConfigUiState,
  onShowScanner: () -> Unit,
  acceptVpnPermission: () -> Unit,
  onRequestCameraPermission: () -> Unit,
  onNavigateToHome: () -> Unit
) {
  Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Column(
        modifier = Modifier.padding(
          8.dp,30.dp, 8.dp,0.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ConfigButton(
          modifier = Modifier,
          imageVector = Icons.Filled.CameraAlt,
          title = stringResource(R.string.config_screen_camera_permission_title),
          subTitle = stringResource(R.string.config_screen_camera_permission_subtitle),
          uiState = uiState.cameraPermissionButtonState,
          onClicked = onRequestCameraPermission
        )

        ConfigButton(
          modifier = Modifier,
          imageVector = Icons.Filled.QrCode,
          title = stringResource(R.string.config_screen_load_configuration_title),
          subTitle = stringResource(R.string.config_screen_load_configuration_subtitle),
          uiState = uiState.qrCodeButtonState,
          enabled = uiState.isQrCodeButtonEnabled,
          onClicked = onShowScanner
        )

        ConfigButton(
          modifier = Modifier,
          imageVector = Icons.Filled.Key,
          title = stringResource(R.string.config_screen_vpn_permission_title),
          subTitle = stringResource(R.string.config_screen_vpn_permission_subtitle),
          uiState = uiState.vpnPermissionButtonState,
          onClicked = acceptVpnPermission
        )

        ConfigButton(
          modifier = Modifier,
          imageVector = Icons.Filled.LiveTv,
          title = stringResource(R.string.config_screen_stream_urls_title),
          subTitle = stringResource(R.string.config_screen_stream_urls_subtitle),
          uiState = uiState.streamURLsButtonState,
          onClicked = onShowScanner
        )
      }

      Button(
        modifier = Modifier.padding(
          0.dp,0.dp, 0.dp,30.dp
        ),
        onClick = onNavigateToHome
      ) {
        Text(
          stringResource(
            if (uiState.canNavigateBackToHome) {
              R.string.config_screen_back_to_home
            } else {
              R.string.config_screen_navigate_to_home
            }
          )
        )
      }
    }
  }
}

@Preview
@Composable
fun ConfigScreenPreview() {
  ConfigScreen(
    uiState = ConfigUiState(
      cameraPermissionButtonState = ConfigButtonState.Done,
      qrCodeButtonState = ConfigButtonState.Done,
      vpnPermissionButtonState = ConfigButtonState.Done,
      streamURLsButtonState = ConfigButtonState.Done
    ),
    onShowScanner = {},
    onNavigateToHome = {},
    onRequestCameraPermission = {},
    acceptVpnPermission = {}
  )
}