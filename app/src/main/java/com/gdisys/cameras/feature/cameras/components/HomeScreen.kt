package com.gdisys.cameras.feature.cameras.components

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gdisys.cameras.R
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

@Composable
fun HomeScreen() {
  val context = LocalContext.current

  val eglBase = remember { EglBase.create() }
  val factory = remember {
    PeerConnectionFactory.initialize(
      PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
        .setEnableInternalTracer(false)
        .createInitializationOptions()
    )
    PeerConnectionFactory.builder()
      .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
      .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
      .createPeerConnectionFactory()
  }

  DisposableEffect(Unit) {
    onDispose {
      factory.dispose()
      eglBase.release()
    }
  }

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
            factory = factory,
            eglBase = eglBase,
            modifier = Modifier.fillMaxSize()
          )
        }
      } else {
        LazyVerticalGrid(
          columns = GridCells.Fixed(1),
          contentPadding = PaddingValues(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
        ) {
          itemsIndexed(streams, key = { _, url -> url }) { index, url ->
            Box(
              modifier = Modifier
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .clickable { focusedStream = url }
            ) {
              WebRtcVideoPlayer(
                streamUrl = url,
                factory = factory,
                eglBase = eglBase,
                modifier = Modifier.fillMaxSize()
              )

              Row(
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .padding(8.dp)
                  .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (index > 0) {
                  IconButton(
                    onClick = {
                      val newList = streams.toMutableList()
                      val item = newList.removeAt(index)
                      newList.add(index - 1, item)
                      streams = newList
                    },
                    modifier = Modifier.size(32.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.KeyboardArrowUp,
                      contentDescription = stringResource(R.string.home_screen_move_up),
                      tint = Color.White
                    )
                  }
                }
                if (index < streams.size - 1) {
                  IconButton(
                    onClick = {
                      val newList = streams.toMutableList()
                      val item = newList.removeAt(index)
                      newList.add(index + 1, item)
                      streams = newList
                    },
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
        }
      }
    }
  }
}