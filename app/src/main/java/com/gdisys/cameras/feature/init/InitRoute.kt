package com.gdisys.cameras.feature.init

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdisys.cameras.feature.config.ConfigUiEvent
import com.gdisys.cameras.feature.config.ConfigViewModel

@Composable
fun InitRoute(
  viewModel: ConfigViewModel,
  onNavigateToConfig: () -> Unit,
  onNavigateToHome: () -> Unit,
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    viewModel.uiEvent.collect { event ->
      when (event) {
        is ConfigUiEvent.ShowToast -> {
          Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  InitScreen(
    uiState,
    showToast = { text ->
      viewModel.showToast(text)
    },
    onNavigateToConfig = onNavigateToConfig,
    onNavigateToHome = onNavigateToHome
  )
}