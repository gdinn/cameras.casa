package com.gdisys.cameras.feature.init

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.core.components.LoadingStorageScreen
import com.gdisys.cameras.feature.config.VpnDataUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Composable
fun InitScreen(
  uiState: VpnDataUiState,
  showToast: (String) -> Unit,
  onNavigateToConfig: () -> Unit,
  onNavigateToHome: () -> Unit
) {

  when (uiState) {
    is VpnDataUiState.Loading -> {
      LoadingStorageScreen()
    }

    is VpnDataUiState.Success -> {
      if (uiState.vpnConfigTokensEmpty) {
        showToast("NO_CREDENTIALS")
        onNavigateToConfig()
      } else {
        onNavigateToHome()
      }
    }
  }
}
