package com.gdisys.cameras.feature.cameras.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import com.gdisys.cameras.core.storage.domain.model.GridPreferences
import com.gdisys.cameras.core.storage.domain.model.StreamOrientation
import org.webrtc.EglBase
import org.webrtc.VideoSink

@Composable
fun HomeScreen(
  streams: List<String>,
  focusedStream: String?,
  grid: GridPreferences,
  orientation: StreamOrientation,
  eglBase: EglBase,
  onConnectStream: (streamUrl: String, videoSink: VideoSink) -> Unit,
  onDisconnectStream: (streamUrl: String) -> Unit,
  onFocusStream: (String) -> Unit,
  onClearFocusedStream: () -> Unit,
  onStreamsReordered: (List<String>) -> Unit,
  onNavigateToConfig: () -> Unit
) {

  val gridState = rememberLazyGridState()
  val overscrollReconfigure = rememberOverscrollReconfigure(gridState)

  rememberSystemBarsController(focusedStream)

  BackHandler(enabled = focusedStream != null) {
    onClearFocusedStream()
  }

  val webRtcConnection = remember(eglBase, onConnectStream, onDisconnectStream) {
    WebRtcConnection(eglBase = eglBase, connect = onConnectStream, disconnect = onDisconnectStream)
  }

  // Each stream's real resolution, filled in by its first frame; until then, 16:9.
  val streamResolutions = remember { StreamResolutionRegistry() }
  val aspectRatioOf: (String) -> Float = { url -> streamResolutions.aspectRatioOf(url) }

  // Movable content per stream URL: lets the same WebRtcVideoPlayer instance (renderer +
  // WHEP connection) move between the grid and the focused view without being disposed
  // and recreated, so the video never restarts when toggling focus.
  val moviePlayers = remember { mutableMapOf<String, @Composable () -> Unit>() }
  fun movablePlayerFor(url: String): @Composable () -> Unit =
    moviePlayers.getOrPut(url) {
      movableContentOf {
        WebRtcVideoPlayer(streamUrl = url, modifier = Modifier.fillMaxSize())
      }
    }

  LaunchedEffect(streams) {
    moviePlayers.keys.retainAll(streams.toSet())
  }

  CompositionLocalProvider(
    LocalWebRtcConnection provides webRtcConnection,
    LocalStreamResolutions provides streamResolutions
  ) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
      Box(
        modifier = Modifier
          .padding(innerPadding)
          .fillMaxSize()
          .background(Color.Black),
        contentAlignment = Alignment.Center
      ) {
        when {
          // The focused stream always counts as visible: it is the only one composed, so it is
          // the only one connected — not even a page change underneath drops it.
          focusedStream != null -> FocusedStreamView(
            videoContent = movablePlayerFor(focusedStream),
            aspectRatio = aspectRatioOf(focusedStream)
          )

          grid.dynamicRows -> DynamicStreamGrid(
            streams = streams,
            grid = grid,
            gridState = gridState,
            overscrollReconfigure = overscrollReconfigure,
            aspectRatioOf = aspectRatioOf,
            onFocusStream = onFocusStream,
            onStreamsReordered = onStreamsReordered,
            onNavigateToConfig = onNavigateToConfig,
            playerFor = ::movablePlayerFor
          )

          else -> FixedStreamGrid(
            streams = streams,
            grid = grid,
            orientation = orientation,
            aspectRatioOf = aspectRatioOf,
            onFocusStream = onFocusStream,
            onStreamsReordered = onStreamsReordered,
            onNavigateToConfig = onNavigateToConfig,
            playerFor = ::movablePlayerFor
          )
        }
      }
    }
  }
}

/** Dynamic mode: vertical scrolling, with "Reconfigure" revealed by overscrolling past the end. */
@Composable
private fun DynamicStreamGrid(
  streams: List<String>,
  grid: GridPreferences,
  gridState: LazyGridState,
  overscrollReconfigure: OverscrollReconfigureState,
  aspectRatioOf: (String) -> Float,
  onFocusStream: (String) -> Unit,
  onStreamsReordered: (List<String>) -> Unit,
  onNavigateToConfig: () -> Unit,
  playerFor: (String) -> @Composable () -> Unit
) {
  Box(modifier = Modifier.fillMaxSize()) {
    ReorderableStreamGrid(
      streams = streams,
      columns = grid.columns,
      gridState = gridState,
      contentPadding = PaddingValues(8.dp),
      onStreamsReordered = onStreamsReordered,
      modifier = Modifier
        .fillMaxSize()
        .background(Color.DarkGray)
        .nestedScroll(overscrollReconfigure.nestedScrollConnection)
    ) { url, dragHandleModifier ->
      CameraGridItem(
        url = url,
        dragHandleModifier = dragHandleModifier,
        videoContent = playerFor(url),
        onFocusedStreamChange = onFocusStream,
        aspectRatio = aspectRatioOf(url)
      )
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

/**
 * Fixed mode: no scrolling, paged, with "Reconfigure" floating over the grid — draggable, since
 * without scrolling there is no other way to move it off a stream.
 */
@Composable
private fun FixedStreamGrid(
  streams: List<String>,
  grid: GridPreferences,
  orientation: StreamOrientation,
  aspectRatioOf: (String) -> Float,
  onFocusStream: (String) -> Unit,
  onStreamsReordered: (List<String>) -> Unit,
  onNavigateToConfig: () -> Unit,
  playerFor: (String) -> @Composable () -> Unit
) {
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.DarkGray)
  ) {
    PagedStreamGrid(
      streams = streams,
      grid = grid,
      orientation = orientation,
      aspectRatioOf = aspectRatioOf,
      onStreamsReordered = onStreamsReordered,
      modifier = Modifier.fillMaxSize()
    ) { url, dragHandleModifier, cellModifier ->
      CameraGridItem(
        url = url,
        dragHandleModifier = dragHandleModifier,
        videoContent = playerFor(url),
        onFocusedStreamChange = onFocusStream,
        modifier = cellModifier
      )
    }

    DraggableReconfigureButton(
      containerWidth = maxWidth,
      containerHeight = maxHeight,
      onClick = onNavigateToConfig
    )
  }
}
