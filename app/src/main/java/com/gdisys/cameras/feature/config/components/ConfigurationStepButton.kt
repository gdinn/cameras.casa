package com.gdisys.cameras.feature.config.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.core.components.LoadingScreen
import com.gdisys.cameras.feature.config.ConfigButtonState

@Composable
fun ConfigButton(
  modifier: Modifier = Modifier,
  imageVector: ImageVector,
  title: String,
  subTitle: String,
  uiState: ConfigButtonState,
  enabled: Boolean = true,
  onClicked: () -> Unit
) {
  Row(
    modifier = Modifier
        .alpha(if (enabled) 1f else 0.5f)
        .clickable(
            onClick = onClicked
        )
        .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 20.dp))
        .background(MaterialTheme.colorScheme.primary)
        .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = imageVector,
      contentDescription = ""
    )
    Spacer(Modifier.width(8.dp))
    Column(
      modifier = Modifier.weight(1f)
    ) {
      Text(
        text = title,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = subTitle
      )
    }
    Row(
      modifier = modifier
          .size(30.dp)
          .clip(RoundedCornerShape(30.dp, 30.dp, 30.dp, 30.dp))
          .background(MaterialTheme.colorScheme.inversePrimary),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
      when (uiState) {
        is ConfigButtonState.Done -> {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = ""
          )
        }

        is ConfigButtonState.Loading -> {
          CircularProgressIndicator(
            modifier = Modifier.padding(4.dp),
            color = Color.White
          )
        }

        is ConfigButtonState.Ready -> {}
        is ConfigButtonState.Error -> {
          Icon(
            imageVector = Icons.Default.Error,
            contentDescription = ""
          )
        }
      }
    }
  }
}

@Preview
@Composable
fun ConfigButtonPreviewLoading() {
  ConfigButton(
    imageVector = Icons.Default.QrCode,
    title = "Load configuration",
    subTitle = "Read configuration QR code to configure Wireguard VPN",
    uiState = ConfigButtonState.Loading,
    onClicked = {}
  )
}

@Preview
@Composable
fun ConfigButtonPreviewReady() {
  ConfigButton(
    imageVector = Icons.Default.QrCode,
    title = "Load configuration",
    subTitle = "Read configuration QR code to configure Wireguard VPN",
    uiState = ConfigButtonState.Ready,
    onClicked = {}
  )
}

@Preview
@Composable
fun ConfigButtonPreviewDone() {
  ConfigButton(
    imageVector = Icons.Default.QrCode,
    title = "Load configuration",
    subTitle = "Read configuration QR code to configure Wireguard VPN",
    uiState = ConfigButtonState.Done,
    onClicked = {}
  )
}

@Preview
@Composable
fun ConfigButtonPreviewError() {
  ConfigButton(
    imageVector = Icons.Default.QrCode,
    title = "Load configuration",
    subTitle = "Read configuration QR code to configure Wireguard VPN",
    uiState = ConfigButtonState.Error,
    onClicked = {}
  )
}
