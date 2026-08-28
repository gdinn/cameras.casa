package com.gdisys.cameras.feature.cameras.components

import android.app.Activity
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gdisys.cameras.R

@Composable
fun HomeScreen(onNavigateToConfig: () -> Unit) {
  val context = LocalContext.current
  val peerConnectionFactory = rememberPeerConnectionFactory()

  var streams by remember {
    mutableStateOf(
      listOf(
        // TODO: Pegar via storage os endpoints finais -> http://[fd00:20::cafe] é padrão por conta do network_security_config
        "http://[fd00:20::cafe]:8889/cam_160",
        "http://[fd00:20::cafe]:8889/cam_161",
        "http://[fd00:20::cafe]:8889/cam_162",
        "http://[fd00:20::cafe]:8889/cam_163"
      )
    )
  }

  var focusedStream by remember { mutableStateOf<String?>(null) }

  val gridState = rememberLazyGridState()
  val overscrollReconfigure = rememberOverscrollReconfigure(gridState)

  LaunchedEffect(focusedStream) {
    val window = (context as? Activity)?.window
    if (window != null) {
      val controller = WindowCompat.getInsetsController(window, window.decorView)
      if (focusedStream != null) {
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      } else {
        controller.show(WindowInsetsCompat.Type.statusBars())
      }
    }
  }

  BackHandler(enabled = focusedStream != null) {
    focusedStream = null
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
        Box(
          modifier = Modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
        ) {
          WebRtcVideoPlayer(
            streamUrl = focusedStream!!,
            factory = peerConnectionFactory.factory,
            eglBase = peerConnectionFactory.eglBase,
            modifier = Modifier.fillMaxSize()
          )
        }
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
                peerConnectionFactory = peerConnectionFactory,
                onFocusedStreamChange = { focusedStream = it },
                onMoveUp = {
                  val newList = streams.toMutableList()
                  val item = newList.removeAt(index)
                  newList.add(index - 1, item)
                  streams = newList
                },
                onMoveDown = {
                  val newList = streams.toMutableList()
                  val item = newList.removeAt(index)
                  newList.add(index + 1, item)
                  streams = newList
                }
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