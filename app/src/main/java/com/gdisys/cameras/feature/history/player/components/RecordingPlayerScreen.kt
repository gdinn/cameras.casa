package com.gdisys.cameras.feature.history.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import com.gdisys.cameras.core.recordings.domain.model.Recording
import com.gdisys.cameras.core.recordings.domain.usecase.RecordingDirection
import com.gdisys.cameras.feature.history.HistoryError
import com.gdisys.cameras.feature.history.player.PlayerStatus
import com.gdisys.cameras.feature.history.player.RecordingPlayerUiState
import com.gdisys.cameras.ui.theme.CamerasTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Recordings are 16:9 (1080p); the player letterboxes anything else. */
private const val VIDEO_ASPECT_RATIO = 16f / 9f

/**
 * One recording, with the player's own controls over the video and previous/next below it.
 *
 * @param video draws the player; the route passes the `RecordingPlayer`'s, tests a placeholder
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingPlayerScreen(
  uiState: RecordingPlayerUiState,
  video: @Composable (Modifier) -> Unit,
  onBack: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onRetry: () -> Unit
) {
  val locale = LocalConfiguration.current.locales[0]
  val start = uiState.recording.start
  val date = remember(start, locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(start) }
  val time = remember(start, locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(start) }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(stringResource(R.string.player_screen_title, date, time))
            Text(uiState.recording.camera, style = MaterialTheme.typography.labelMedium)
          }
        },
        navigationIcon = {
          IconButton(modifier = Modifier.testTag(RecordingPlayerTestTags.BACK_BUTTON), onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.history_screen_navigate_back)
            )
          }
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(VIDEO_ASPECT_RATIO)
          // Letterbox behind video stays black regardless of the theme.
          .background(Color.Black),
        contentAlignment = Alignment.Center
      ) {
        video(Modifier.fillMaxSize().testTag(RecordingPlayerTestTags.VIDEO))
        StatusOverlay(status = uiState.status, onRetry = onRetry)
      }

      if (uiState.nearestNotice) {
        Text(
          text = stringResource(R.string.player_nearest_notice, time),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier
            .padding(horizontal = 16.dp)
            .testTag(RecordingPlayerTestTags.NEAREST_NOTICE)
        )
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        NeighborButton(
          label = stringResource(R.string.player_previous),
          icon = { Icon(Icons.Filled.SkipPrevious, contentDescription = null) },
          looking = uiState.looking == RecordingDirection.PREVIOUS,
          enabled = uiState.looking == null,
          onClick = onPrevious,
          modifier = Modifier
            .weight(1f)
            .testTag(RecordingPlayerTestTags.PREVIOUS_BUTTON)
        )
        NeighborButton(
          label = stringResource(R.string.player_next),
          icon = { Icon(Icons.Filled.SkipNext, contentDescription = null) },
          looking = uiState.looking == RecordingDirection.NEXT,
          enabled = uiState.looking == null,
          onClick = onNext,
          modifier = Modifier
            .weight(1f)
            .testTag(RecordingPlayerTestTags.NEXT_BUTTON)
        )
      }
    }
  }
}

/** What the video area says over the player when it is not simply playing. */
@Composable
private fun StatusOverlay(status: PlayerStatus, onRetry: () -> Unit) {
  when (status) {
    PlayerStatus.Playing -> Unit

    PlayerStatus.Loading -> CircularProgressIndicator(
      color = Color.White,
      modifier = Modifier.testTag(RecordingPlayerTestTags.LOADING)
    )

    PlayerStatus.Ended -> OverlayMessage(
      text = stringResource(R.string.player_ended),
      modifier = Modifier.testTag(RecordingPlayerTestTags.ENDED)
    )

    is PlayerStatus.Failed -> OverlayMessage(
      text = stringResource(status.error.resId),
      modifier = Modifier.testTag(RecordingPlayerTestTags.ERROR)
    ) {
      Button(onClick = onRetry, modifier = Modifier.testTag(RecordingPlayerTestTags.RETRY_BUTTON)) {
        Text(stringResource(R.string.history_screen_retry))
      }
    }
  }
}

@Composable
private fun OverlayMessage(
  text: String,
  modifier: Modifier = Modifier,
  action: @Composable () -> Unit = {}
) {
  // Opaque: the last frame stays under it otherwise, and white text would sit on a bright image.
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
      .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(text = text, color = Color.White, textAlign = TextAlign.Center)
    action()
  }
}

@Composable
private fun NeighborButton(
  label: String,
  icon: @Composable () -> Unit,
  looking: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier) {
    if (looking) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else icon()
    Text(text = label, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
  }
}

@Preview
@Composable
private fun RecordingPlayerScreenPreview() {
  CamerasTheme {
    RecordingPlayerScreen(
      uiState = RecordingPlayerUiState(
        recording = Recording("cam_10", LocalDateTime.of(2026, 11, 10, 15, 10), "cam_10_2026-11-10_15-10-00.mp4"),
        nearestNotice = true,
        status = PlayerStatus.Failed(HistoryError.UNREACHABLE)
      ),
      video = {},
      onBack = {},
      onPrevious = {},
      onNext = {},
      onRetry = {}
    )
  }
}
