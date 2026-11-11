package com.gdisys.cameras.core.vpn.domain

import kotlinx.coroutines.flow.Flow

/**
 * The single owner of the tunnel. Whoever needs it registers as a [TunnelHolder]: the tunnel goes up
 * with the first holder and down once the last one is gone, after a short grace period, so a
 * hand-over between two screens (one pauses before the other resumes) does not drop it.
 *
 * Connect and disconnect never overlap, the last demand always wins, and an operation that started
 * always finishes.
 */
interface TunnelCoordinator {

  /**
   * A connect that failed. Holders that care (a screen on display) react to it; the coordinator
   * does not retry on its own. The next [request] after a failure tries again, even from a holder
   * that never left.
   */
  val connectFailures: Flow<Throwable>

  /** Adds [holder]; the tunnel goes up if it is not already. Repeating a request is harmless. */
  fun request(holder: TunnelHolder)

  /** Removes [holder]; once no holder is left, the tunnel goes down after the grace period. */
  fun release(holder: TunnelHolder)

  /**
   * Adds [holder] only while the tunnel is up, and never raises it: `false` when it is down, still
   * coming up or on its way down. This is how work that must not open the tunnel by itself (a
   * download) keeps an existing one up.
   */
  fun holdIfUp(holder: TunnelHolder): Boolean

  /**
   * Whether [holder] is registered. Holders live in memory, so a download worker that finds its
   * holder gone is running in a new process: the one that took the holder died.
   */
  fun holds(holder: TunnelHolder): Boolean

  /** Drops every holder and takes the tunnel down at once, with no grace period (app closed). */
  fun clear()
}

/** Something that keeps the tunnel up while it is registered. */
sealed interface TunnelHolder {
  /** A screen on display, by a name unique among screens. */
  data class Screen(val name: String) : TunnelHolder

  /** A download, by its work id. */
  data class Download(val workId: String) : TunnelHolder
}
