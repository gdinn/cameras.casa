package com.gdisys.cameras.core.vpn.data

import com.gdisys.cameras.core.coroutines.ApplicationScope
import com.gdisys.cameras.core.storage.domain.usecase.GetVpnConfigUseCase
import com.gdisys.cameras.core.vpn.domain.TunnelCoordinator
import com.gdisys.cameras.core.vpn.domain.TunnelHolder
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import com.gdisys.cameras.core.vpn.domain.VpnTunnelState
import com.gdisys.cameras.core.vpn.domain.usecase.ConnectVpnUseCase
import com.gdisys.cameras.core.vpn.domain.usecase.DisconnectVpnUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** How long the tunnel stays up after its last holder leaves, in milliseconds. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TunnelReleaseGrace

/**
 * Production [TunnelCoordinator].
 *
 * The holders become a *wish* ("up", or "down" once the grace period passed with no holder), kept in
 * a `StateFlow`: it conflates, and its collector skips a wish equal to the last one it applied. A
 * single collector applies it, so calls never overlap and the last wish wins: a quick release and
 * request while a connect runs leaves nothing to do, and a release that arrives during a connect is
 * applied once the connect returns. Not `collectLatest`, which would cancel a native `setState`
 * halfway; the operations run in `NonCancellable` for the same reason.
 *
 * The coordinator only takes down what it brought up: before the first connect, "down" does nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TunnelCoordinatorImpl @Inject constructor(
  private val getVpnConfigUseCase: GetVpnConfigUseCase,
  private val connectVpnUseCase: ConnectVpnUseCase,
  private val disconnectVpnUseCase: DisconnectVpnUseCase,
  private val vpnRepository: VpnRepository,
  @ApplicationScope scope: CoroutineScope,
  @param:TunnelReleaseGrace private val releaseGraceMillis: Long
) : TunnelCoordinator {

  /**
   * [attempt] grows on the first request after a failed connect, so the wish changes and the
   * collector tries again even though the tunnel was already wanted. [immediate] skips the grace
   * period ([clear]).
   */
  private data class Demand(
    val holders: Set<TunnelHolder> = emptySet(),
    val attempt: Int = 0,
    val immediate: Boolean = false
  )

  private sealed interface Wish {
    data class Up(val attempt: Int) : Wish
    data object Down : Wish
  }

  private val lock = Any()
  private val demand = MutableStateFlow(Demand())

  @Volatile
  private var lastConnectFailed = false

  private val _connectFailures = MutableSharedFlow<Throwable>(extraBufferCapacity = 8)
  override val connectFailures: Flow<Throwable> = _connectFailures.asSharedFlow()

  /** `null` until the first wish: nothing has been decided at startup. */
  private val wish: StateFlow<Wish?> = demand
    .transformLatest { current ->
      if (current.holders.isEmpty()) {
        // Cancelled by the next change of demand: a request within the grace period keeps the tunnel.
        if (!current.immediate) delay(releaseGraceMillis)
        emit(Wish.Down)
      } else {
        emit(Wish.Up(current.attempt))
      }
    }
    .stateIn(scope, SharingStarted.Eagerly, null)

  init {
    scope.launch {
      var tunnelRequested = false
      wish.filterNotNull().collect { next ->
        when (next) {
          is Wish.Up -> {
            tunnelRequested = true
            connect()
          }

          Wish.Down -> if (tunnelRequested) {
            tunnelRequested = false
            withContext(NonCancellable) { disconnectVpnUseCase() }
          }
        }
      }
    }
  }

  override fun request(holder: TunnelHolder) = synchronized(lock) {
    val retry = lastConnectFailed
    lastConnectFailed = false
    demand.update {
      it.copy(holders = it.holders + holder, attempt = if (retry) it.attempt + 1 else it.attempt, immediate = false)
    }
  }

  override fun release(holder: TunnelHolder) = synchronized(lock) {
    demand.update { it.copy(holders = it.holders - holder) }
  }

  override fun holdIfUp(holder: TunnelHolder): Boolean = synchronized(lock) {
    // The wish is still "up" during the grace period, so a holder arriving then keeps the tunnel.
    val up = wish.value is Wish.Up && vpnRepository.vpnState.value == VpnTunnelState.CONNECTED
    if (up) demand.update { it.copy(holders = it.holders + holder, immediate = false) }
    up
  }

  override fun holds(holder: TunnelHolder): Boolean = synchronized(lock) { holder in demand.value.holders }

  override fun clear() = synchronized(lock) {
    demand.update { it.copy(holders = emptySet(), immediate = true) }
  }

  private suspend fun connect() {
    val result = withContext(NonCancellable) {
      getVpnConfigUseCase().mapCatching { config -> connectVpnUseCase(config).getOrThrow() }
    }
    result.onFailure { e ->
      lastConnectFailed = true
      _connectFailures.tryEmit(e)
    }
  }
}
