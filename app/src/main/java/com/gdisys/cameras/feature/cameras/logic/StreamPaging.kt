package com.gdisys.cameras.feature.cameras.logic

/**
 * Paging for fixed mode: the grid does not scroll, so whatever does not fit in `rows x columns`
 * moves to the following pages, filled *row-major* in the current orientation's order.
 *
 * Everything here is a pure function over the list of URLs, which is what makes it testable on the
 * JVM with no emulator.
 */

/** Number of streams per page. Never below 1, so nothing divides by zero. */
fun itemsPerPage(rows: Int, columns: Int): Int = (rows * columns).coerceAtLeast(1)

/** Pages needed for [streamCount] streams; at least one, which may be empty. */
fun pageCount(streamCount: Int, itemsPerPage: Int): Int {
  if (itemsPerPage <= 0) return 1
  val pages = (streamCount + itemsPerPage - 1) / itemsPerPage
  return pages.coerceAtLeast(1)
}

/** Streams on page [page]; the last page may be partial. */
fun streamsOnPage(streams: List<String>, page: Int, itemsPerPage: Int): List<String> {
  if (itemsPerPage <= 0 || page < 0) return emptyList()
  return streams.drop(page * itemsPerPage).take(itemsPerPage)
}

/**
 * Moves [url] to [targetIndex] of the complete list, preserving the rest of the order.
 *
 * The index is the one in the list **before** the removal — that is what the UI knows when it
 * drags an item over another cell.
 */
fun reorderedTo(order: List<String>, url: String, targetIndex: Int): List<String> {
  val without = order.filterNot { it == url }
  if (without.size == order.size) return order
  val index = targetIndex.coerceIn(0, without.size)
  return without.toMutableList().apply { add(index, url) }
}

/**
 * Moves [url] to page [page] — what happens when a dragged item is held against an edge and the
 * page advances by itself.
 *
 * @param atStart `true` when the item enters from the left (advancing to the next page); `false`
 *   when it enters from the right (going back to the previous one).
 */
fun movedToPage(
  order: List<String>,
  url: String,
  page: Int,
  itemsPerPage: Int,
  atStart: Boolean
): List<String> {
  val without = order.filterNot { it == url }
  if (without.size == order.size) return order
  val firstOnPage = page * itemsPerPage
  val index = if (atStart) firstOnPage else firstOnPage + itemsPerPage - 1
  return without.toMutableList().apply { add(index.coerceIn(0, without.size), url) }
}
