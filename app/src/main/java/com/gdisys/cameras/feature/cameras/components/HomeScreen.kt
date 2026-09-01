package com.gdisys.cameras.feature.cameras.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import com.gdisys.cameras.core.webrtc.domain.WhepClient
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

@Composable
fun HomeScreen(
  streams: List<String>,
  focusedStream: String?,
  eglBase: EglBase,
  onStartWhepConnection: suspend (streamUrl: String, renderer: SurfaceViewRenderer) -> WhepClient?,
  onFocusStream: (String) -> Unit,
  onClearFocusedStream: () -> Unit,
  onMoveStreamUp: (Int) -> Unit,
  onMoveStreamDown: (Int) -> Unit,
  onNavigateToConfig: () -> Unit
) {

  val gridState = rememberLazyGridState()
  val overscrollReconfigure = rememberOverscrollReconfigure(gridState)

  rememberSystemBarsController(focusedStream)

  BackHandler(enabled = focusedStream != null) {
    onClearFocusedStream()
  }

  Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
    Box(
      modifier = Modifier
        .padding(innerPadding)
        .fillMaxSize()
        .background(Color.Black),
      contentAlignment = Alignment.Center
    ) {
      if (focusedStream != null) {
        FocusedStreamView(
          streamUrl = focusedStream,
          eglBase = eglBase,
          onStartWhepConnection = onStartWhepConnection
        )
      } else {
        Box(modifier = Modifier.fillMaxSize()) {
          LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(1),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
              .fillMaxSize()
              .background(Color.DarkGray)
              .nestedScroll(overscrollReconfigure.nestedScrollConnection)
          ) {
            itemsIndexed(streams, key = { _, url -> url }) { index, url ->
              CameraGridItem(
                url = url,
                canMoveUp = index > 0,
                canMoveDown = index < streams.size - 1,
                eglBase = eglBase,
                onStartWhepConnection = onStartWhepConnection,
                onFocusedStreamChange = onFocusStream,
                onMoveUp = { onMoveStreamUp(index) },
                onMoveDown = { onMoveStreamDown(index) }
              )
            }
          }

          AnimatedVisibility(
            visible = overscrollReconfigure.isReconfigureButtonVisible,
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(16.dp),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
          ) {
            Button(onClick = onNavigateToConfig) {
              Text(text = stringResource(R.string.home_screen_reconfigure))
            }
          }
        }
      }
    }
  }
}