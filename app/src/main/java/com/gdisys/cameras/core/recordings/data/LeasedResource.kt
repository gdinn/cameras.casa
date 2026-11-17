package com.gdisys.cameras.core.recordings.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One shared, expensive resource (the SMB connection), opened on first use, shared by concurrent
 * users and closed once nobody used it for [idleMillis].
 *
 * - **Key:** the resource is opened for a key (the login). A use with another key closes the
 *   current resource, even if it is still in use, and opens a new one.
 * - **Broken resource:** when [isBroken] says a failure means the resource is dead (the tunnel went
 *   down while it sat idle), it is closed. If it was a reused one, the use is retried once on a
 *   fresh resource; a failure on a fresh one is the caller's.
 * - **Closing never holds anyone up.** A resource is taken out under the lock and closed after it,
 *   in [scope]: closing a dead connection can wait for timeouts, and every use needs the lock.
 *   [close] failures are ignored, since the resource is being dropped anyway.
 * - [invalidate] drops the current resource at once, for when its connection is known to be dead
 *   (the tunnel went down).
 */
internal class LeasedResource<K : Any, R : Any>(
  private val scope: CoroutineScope,
  private val idleMillis: Long,
  private val open: suspend (K) -> R,
  private val close: suspend (R) -> Unit,
  private val isBroken: (Throwable) -> Boolean
) {

  private class Entry<K, R>(val key: K, val resource: R) {
    var leases = 0
  }

  private val mutex = Mutex()
  private var current: Entry<K, R>? = null
  private var idleClose: Job? = null

  suspend fun <T> use(key: K, block: suspend (R) -> T): T {
    val (entry, fresh) = acquire(key)
    try {
      return block(entry.resource)
    } catch (e: Throwable) {
      if (e is CancellationException || !isBroken(e)) throw e
      discard(entry)
      if (fresh) throw e
    } finally {
      release(entry)
    }
    val (retry, _) = acquire(key)
    try {
      return block(retry.resource)
    } catch (e: Throwable) {
      if (e !is CancellationException && isBroken(e)) discard(retry)
      throw e
    } finally {
      release(retry)
    }
  }

  private suspend fun acquire(key: K): Pair<Entry<K, R>, Boolean> = mutex.withLock {
    idleClose?.cancel()
    idleClose = null
    val existing = current
    if (existing != null && existing.key == key) {
      existing.leases++
      return existing to false
    }
    if (existing != null) {
      current = null
      closeLater(existing.resource)
    }
    val entry = Entry(key, open(key))
    entry.leases = 1
    current = entry
    entry to true
  }

  private suspend fun release(entry: Entry<K, R>) = withContext(NonCancellable) {
    mutex.withLock {
      entry.leases--
      if (current === entry && entry.leases == 0) {
        idleClose = scope.launch {
          delay(idleMillis)
          val idle = mutex.withLock {
            (current === entry && entry.leases == 0).also { if (it) current = null }
          }
          if (idle) closeQuietly(entry.resource)
        }
      }
    }
  }

  /** Drops the current resource, in use or not; the next use opens a new one. */
  suspend fun invalidate() = withContext(NonCancellable) {
    mutex.withLock {
      current?.let { dropLocked(it) }
    }
  }

  private suspend fun discard(entry: Entry<K, R>) = withContext(NonCancellable) {
    mutex.withLock {
      if (current === entry) dropLocked(entry)
    }
  }

  /** Under the lock: forgets [entry] and closes it once the lock is released. */
  private fun dropLocked(entry: Entry<K, R>) {
    current = null
    idleClose?.cancel()
    idleClose = null
    closeLater(entry.resource)
  }

  private fun closeLater(resource: R) {
    scope.launch { closeQuietly(resource) }
  }

  private suspend fun closeQuietly(resource: R) {
    try {
      close(resource)
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      // Dropped anyway.
    }
  }
}
