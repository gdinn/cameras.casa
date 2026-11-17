package com.gdisys.cameras.core.downloads.data

/**
 * [name] if it is free, else the first free `name_1.ext`, `name_2.ext`, ... A download never
 * overwrites a file, and the suffix keeps the recording's name readable.
 */
internal fun freeFileName(name: String, isTaken: (String) -> Boolean): String {
  if (!isTaken(name)) return name
  val dot = name.lastIndexOf('.')
  val base = if (dot > 0) name.substring(0, dot) else name
  val extension = if (dot > 0) name.substring(dot) else ""
  return generateSequence(1) { it + 1 }
    .map { "${base}_$it$extension" }
    .first { !isTaken(it) }
}
