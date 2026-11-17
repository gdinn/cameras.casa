package com.gdisys.cameras.core.recordings.domain.usecase

import kotlinx.coroutines.CancellationException

/**
 * [block]'s value, or its failure as a [Result]. Unlike `runCatching`, it lets a cancellation
 * through: a screen that leaves while a listing runs must not see it as an error.
 */
internal suspend inline fun <T> recordingsResult(crossinline block: suspend () -> T): Result<T> = try {
  Result.success(block())
} catch (e: CancellationException) {
  throw e
} catch (e: Exception) {
  Result.failure(e)
}
