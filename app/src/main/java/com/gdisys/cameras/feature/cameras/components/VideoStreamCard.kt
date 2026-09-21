package com.gdisys.cameras.feature.cameras.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * @param aspectRatio ratio applied to the card; `null` when the size already arrives measured from
 *   outside (fixed mode, where [FixedGrid] computes the row height).
 */
@Composable
fun VideoStreamCard(
  videoContent: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  aspectRatio: Float? = null,
  onClick: (() -> Unit)? = null,
  content: @Composable BoxScope.() -> Unit = {}
) {
  Box(
    modifier = modifier
      .let { if (aspectRatio != null) it.aspectRatio(aspectRatio) else it }
      .clip(RoundedCornerShape(8.dp))
      .let { if (onClick != null) it.clickable(onClick = onClick) else it }
  ) {
    videoContent()
    content()
  }
}
