package com.gdisys.cameras.feature.cameras.domain

/**
 * Paginação do modo fixo: a grade não tem scroll, então o que não cabe em `linhas × colunas` vai
 * para as páginas seguintes, preenchidas em *row-major* seguindo a ordem da orientação atual.
 *
 * Tudo aqui é função pura sobre a lista de URLs — é o que torna a etapa testável sem emulador.
 */

/** Quantidade de streams por página. Nunca menor que 1, para não dividir por zero. */
fun itemsPerPage(rows: Int, columns: Int): Int = (rows * columns).coerceAtLeast(1)

/** Número de páginas necessárias para [streamCount] streams; no mínimo uma (página vazia). */
fun pageCount(streamCount: Int, itemsPerPage: Int): Int {
  if (itemsPerPage <= 0) return 1
  val pages = (streamCount + itemsPerPage - 1) / itemsPerPage
  return pages.coerceAtLeast(1)
}

/** Streams da página [page]; a última pode vir incompleta. */
fun streamsOnPage(streams: List<String>, page: Int, itemsPerPage: Int): List<String> {
  if (itemsPerPage <= 0 || page < 0) return emptyList()
  return streams.drop(page * itemsPerPage).take(itemsPerPage)
}

/**
 * Move [url] para [targetIndex] da lista completa, preservando o resto da ordem.
 *
 * O índice é o da lista **antes** da remoção — é o que a UI conhece ao passar o item arrastado por
 * cima de outra célula.
 */
fun reorderedTo(order: List<String>, url: String, targetIndex: Int): List<String> {
  val without = order.filterNot { it == url }
  if (without.size == order.size) return order
  val index = targetIndex.coerceIn(0, without.size)
  return without.toMutableList().apply { add(index, url) }
}

/**
 * Move [url] para a página [page] — o que acontece quando o item arrastado é segurado na borda e a
 * página avança sozinha.
 *
 * @param atStart `true` quando o item entra pela esquerda (avanço para a próxima página); `false`
 *   quando entra pela direita (volta para a anterior).
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
