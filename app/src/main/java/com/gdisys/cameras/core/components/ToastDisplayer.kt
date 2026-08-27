package com.gdisys.cameras.core.components

import android.content.Context
import android.content.res.Resources
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.gdisys.cameras.feature.config.ConfigUiEvent
import com.gdisys.cameras.feature.config.ConfigViewModel

@Composable
fun ToastDisplayer(
  viewModel: ConfigViewModel,
  context: Context,
  resources: Resources
) {
  LaunchedEffect(viewModel) {
    viewModel.uiEvent.collect { event ->
      when (event) {
        is ConfigUiEvent.ShowToast -> {
          Toast.makeText(
            context,
            resources.getString(event.configToastMessage.resId),
            Toast.LENGTH_SHORT
          ).show()
        }
      }
    }
  }
}