package com.gdisys.cameras.core.storage.domain.model

/**
 * Returns the preferences with [StreamPreferences.portraitOrder] and
 * [StreamPreferences.landscapeOrder] aligned to the canonical set [StreamPreferences.streamUrls]:
 * URLs that no longer exist are dropped, and missing ones are appended in the order they appear in
 * the canonical set.
 *
 * Applied on read, and only on read, this is the single point that keeps the set and the orders
 * from diverging silently.
 */
fun StreamPreferences.reconciled(): StreamPreferences = copy(
  portraitOrder = streamUrls.reconcile(portraitOrder),
  landscapeOrder = streamUrls.reconcile(landscapeOrder)
)

/** Display order for [orientation]. */
fun StreamPreferences.orderFor(orientation: StreamOrientation): List<String> = when (orientation) {
  StreamOrientation.PORTRAIT -> portraitOrder
  StreamOrientation.LANDSCAPE -> landscapeOrder
}

/** Grid configuration for [orientation]. */
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
