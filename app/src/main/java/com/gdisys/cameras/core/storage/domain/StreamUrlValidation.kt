package com.gdisys.cameras.core.storage.domain

import com.gdisys.cameras.core.network.STREAM_URL_HOST_PREFIX

private const val MIN_PORT = 1
private const val MAX_PORT = 65535

private val STREAM_NAME_REGEX = Regex("^[A-Za-z0-9_-]+$")

/** Outcome of validating a new stream URL. */
sealed interface StreamUrlValidationResult {
  /** Valid, not yet registered, and already assembled from the port and the name. */
  data class Valid(val url: String) : StreamUrlValidationResult

  /** Port missing, non-numeric, or outside the 1..65535 range. */
  data object InvalidPort : StreamUrlValidationResult

  /** Name empty, or holding characters outside `^[A-Za-z0-9_-]+$`. */
  data object InvalidStreamName : StreamUrlValidationResult

  /** URL already in the list, compared case-insensitively. */
  data object DuplicateUrl : StreamUrlValidationResult
}

/**
 * Validates the port and the stream name, and assembles the full URL.
 *
 * The port is normalized to its numeric form (`"0080"` -> `"80"`), which also rules out duplicates
 * that would differ only by leading zeros. The name is kept exactly as typed; the duplicate check
 * against [existingUrls] is case-insensitive all the same.
 */
fun validateStreamUrl(
  port: String,
  streamName: String,
  existingUrls: List<String>
): StreamUrlValidationResult {
  val parsedPort = port.toIntOrNull()
  if (parsedPort == null || parsedPort !in MIN_PORT..MAX_PORT) {
    return StreamUrlValidationResult.InvalidPort
  }

  if (!STREAM_NAME_REGEX.matches(streamName)) {
    return StreamUrlValidationResult.InvalidStreamName
  }

  val url = "$STREAM_URL_HOST_PREFIX$parsedPort/$streamName"
  if (existingUrls.any { it.equals(url, ignoreCase = true) }) {
    return StreamUrlValidationResult.DuplicateUrl
  }

  return StreamUrlValidationResult.Valid(url)
}
