package com.gdisys.cameras.feature.cameras.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R

/**
 * Home with no URL registered. Unlike dynamic mode, "Reconfigure" is fixed in place here: there is
 * no list to overscroll and reveal it.
 */
@Composable
fun EmptyStreamsScreen(
  modifier: Modifier = Modifier,
  onNavigateToConfig: () -> Unit
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = stringResource(R.string.home_screen_no_streams),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center
    )
    Button(onClick = onNavigateToConfig) {
      Text(text = stringResource(R.string.home_screen_reconfigure))
    }
  }
}

@Preview
@Composable
fun EmptyStreamsScreenPreview() {
  EmptyStreamsScreen(onNavigateToConfig = {})
}
