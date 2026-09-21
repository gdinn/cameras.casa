package com.gdisys.cameras.core.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow

@Composable
fun <Input, Output> LaunchActivityResultOnEvent(
  events: Flow<Input>,
  contract: ActivityResultContract<Input, Output>,
  onResult: (Output) -> Unit
) {
  val launcher = rememberLauncherForActivityResult(contract, onResult)
  LaunchedEffect(events) {
    events.collect { input ->
      launcher.launch(input)
    }
  }
}
