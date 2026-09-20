package com.gdisys.cameras.core.storage.domain.model

/**
 * Devolve as preferências com [StreamPreferences.portraitOrder] e
 * [StreamPreferences.landscapeOrder] alinhadas ao conjunto canônico
 * [StreamPreferences.streamUrls]: URLs que não existem mais são descartadas e as que faltam entram
 * no fim, na ordem em que aparecem no canônico.
 *
 * Aplicada na leitura (e só nela), é o ponto único que impede divergência silenciosa entre o
 * conjunto e as ordens.
 */
fun StreamPreferences.reconciled(): StreamPreferences = copy(
  portraitOrder = streamUrls.reconcile(portraitOrder),
  landscapeOrder = streamUrls.reconcile(landscapeOrder)
)

/** Ordem de exibição da orientação [orientation]. */
fun StreamPreferences.orderFor(orientation: StreamOrientation): List<String> = when (orientation) {
  StreamOrientation.PORTRAIT -> portraitOrder
  StreamOrientation.LANDSCAPE -> landscapeOrder
}

/** Configuração de grade da orientação [orientation]. */
fun StreamPreferences.gridFor(orientation: StreamOrientation): GridPreferences =
  when (orientation) {
    StreamOrientation.PORTRAIT -> portraitGrid
    StreamOrientation.LANDSCAPE -> landscapeGrid
  }

private fun List<String>.reconcile(order: List<String>): List<String> {
  val canonical = toSet()
  val kept = order.filter { it in canonical }.distinct()
  val keptUrls = kept.toSet()
  return kept + filterNot { it in keptUrls }
}
