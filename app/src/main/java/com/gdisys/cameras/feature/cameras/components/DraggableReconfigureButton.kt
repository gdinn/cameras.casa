package com.gdisys.cameras.feature.cameras.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gdisys.cameras.R
import kotlin.math.roundToInt

/**
 * "Reconfigure" flutuando sobre a grade do modo fixo.
 *
 * Como aqui não há scroll para revelá-lo (é o papel do `OverscrollReconfigure` no modo dinâmico),
 * o botão fica sempre visível — e arrastável, para sair da frente do stream que estiver atrás dele.
 *
 * A posição é um deslocamento a partir do canto inferior direito, limitado ao tamanho do
 * contêiner, de modo que o botão nunca escapa da tela.
 *
 * @param containerWidth largura disponível, usada para limitar o arraste
 * @param containerHeight altura disponível, usada para limitar o arraste
 */
@Composable
fun BoxScope.DraggableReconfigureButton(
  containerWidth: Dp,
  containerHeight: Dp,
  onClick: () -> Unit,
  margin: Dp = 16.dp
) {
  var offset by remember { mutableStateOf(Offset.Zero) }
  var buttonSize by remember { mutableStateOf(IntSize.Zero) }

  val currentContainerWidth by rememberUpdatedState(containerWidth)
  val currentContainerHeight by rememberUpdatedState(containerHeight)
  val currentMargin by rememberUpdatedState(margin)

  ExtendedFloatingActionButton(
    onClick = onClick,
    modifier = Modifier
      .align(Alignment.BottomEnd)
      .padding(currentMargin)
      .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
      .onSizeChanged { buttonSize = it }
      .pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
          change.consume()
          val marginPx = currentMargin.toPx()
          // A âncora é o canto inferior direito: o deslocamento só pode ser negativo, e no
          // máximo até o canto oposto.
          val minX = -(currentContainerWidth.toPx() - buttonSize.width - 2 * marginPx)
          val minY = -(currentContainerHeight.toPx() - buttonSize.height - 2 * marginPx)
          offset = Offset(
            x = (offset.x + dragAmount.x).coerceIn(minX.coerceAtMost(0f), 0f),
            y = (offset.y + dragAmount.y).coerceIn(minY.coerceAtMost(0f), 0f)
          )
        }
      }
  ) {
    Text(text = stringResource(R.string.home_screen_reconfigure))
  }
}
