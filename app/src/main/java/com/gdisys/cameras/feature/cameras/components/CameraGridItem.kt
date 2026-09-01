package com.gdisys.cameras.feature.cameras.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import com.gdisys.cameras.core.webrtc.domain.WhepClient
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

@Composable
fun CameraGridItem(
  url: String,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  eglBase: EglBase,
  onStartWhepConnection: suspend (streamUrl: String, renderer: SurfaceViewRenderer) -> WhepClient?,
  onFocusedStreamChange: (String) -> Unit,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit
) {
  Box(
    modifier = Modifier
      .aspectRatio(16f / 9f)
      .clip(RoundedCornerShape(8.dp))
      .clickable { onFocusedStreamChange(url) }
  ) {
    WebRtcVideoPlayer(
      streamUrl = url,
      eglBase = eglBase,
      onStartWhepConnection = onStartWhepConnection,
      modifier = Modifier.fillMaxSize()
    )

    Row(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(8.dp)
        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (canMoveUp) {
        IconButton(
          onClick = onMoveUp,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = stringResource(R.string.home_screen_move_up),
            tint = Color.White
          )
        }
      }
      if (canMoveDown) {
        IconButton(
          onClick = onMoveDown,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.home_screen_move_down),
            tint = Color.White
          )
        }
      }
    }
  }
}
