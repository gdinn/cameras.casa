package com.gdisys.cameras.feature.init

import androidx.compose.runtime.Composable
import com.gdisys.cameras.core.components.LoadingStorageScreen
import com.gdisys.cameras.core.vpn.domain.VpnDataUiState

@Composable
fun InitScreen(
  uiState: VpnDataUiState,
  showToast: (InitToastMessage) -> Unit,
  onNavigateToConfig: () -> Unit,
  onNavigateToHome: () -> Unit
) {

  when (uiState) {
    is VpnDataUiState.Loading -> {
      LoadingStorageScreen()
    }

    is VpnDataUiState.Success -> {
      if (uiState.vpnConfigTokensEmpty) {
        showToast(InitToastMessage.CREDENTIALS_NOT_FOUND)
        onNavigateToConfig()
      } else {
        onNavigateToHome()
      }
    }
  }
}
