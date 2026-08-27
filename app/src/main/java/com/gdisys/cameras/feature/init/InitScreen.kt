package com.gdisys.cameras.feature.init

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gdisys.cameras.R
import com.gdisys.cameras.core.components.LoadingStorageScreen
import com.gdisys.cameras.feature.config.ConfigToastMessage
import com.gdisys.cameras.feature.config.VpnDataUiState

@Composable
fun InitScreen(
  uiState: VpnDataUiState,
  showToast: (ConfigToastMessage) -> Unit,
  onNavigateToConfig: () -> Unit,
  onNavigateToHome: () -> Unit
) {

  when (uiState) {
    is VpnDataUiState.Loading -> {
      LoadingStorageScreen()
    }

    is VpnDataUiState.Success -> {
      if (uiState.vpnConfigTokensEmpty) {
        showToast(ConfigToastMessage.CREDENTIALS_NOT_FOUND)
        onNavigateToConfig()
      } else {
        onNavigateToHome()
      }
    }
  }
}
