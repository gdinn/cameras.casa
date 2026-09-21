package com.gdisys.cameras.feature.cameras.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R

/**
 * Célula da grade.
 *
 * O arraste sai de um *handle* dedicado, e não do card inteiro, para não disputar com o toque que
 * coloca o stream em foco. [dragHandleModifier] é fornecido pela [ReorderableStreamGrid] (modo
 * dinâmico) ou pela [PagedStreamGrid] (modo fixo).
 *
 * @param aspectRatio proporção real do vídeo; no modo fixo é `null`, porque o [modifier] já chega
 *   com o tamanho medido pela [FixedGrid].
 */
@Composable
fun CameraGridItem(
  url: String,
  dragHandleModifier: Modifier,
  videoContent: @Composable () -> Unit,
  onFocusedStreamChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  aspectRatio: Float? = null
) {
  VideoStreamCard(
    videoContent = videoContent,
    modifier = modifier,
    aspectRatio = aspectRatio,
    onClick = { onFocusedStreamChange(url) }
  ) {
    Icon(
      imageVector = Icons.Default.DragHandle,
      contentDescription = stringResource(R.string.home_screen_drag_handle),
      tint = Color.White,
      modifier = dragHandleModifier
        .align(Alignment.TopEnd)
        .padding(8.dp)
        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
        .size(32.dp)
        .padding(4.dp)
    )
  }
}
