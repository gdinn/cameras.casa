package com.gdisys.cameras.core.vpn.di

import com.gdisys.cameras.core.vpn.data.TunnelCoordinatorImpl
import com.gdisys.cameras.core.vpn.data.TunnelReleaseGrace
import com.gdisys.cameras.core.vpn.data.VpnLifecycleControllerImpl
import com.gdisys.cameras.core.vpn.data.VpnRepositoryImpl
import com.gdisys.cameras.core.vpn.domain.TunnelCoordinator
import com.gdisys.cameras.core.vpn.domain.VpnLifecycleController
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Longer than a navigation transition (navigation-compose fades for 700 ms), during which the
 * screen being left is already paused and the next one not yet resumed.
 */
private const val TUNNEL_RELEASE_GRACE_MS = 1_500L

@Module
@InstallIn(SingletonComponent::class)
abstract class VpnModule {

  @Binds
  @Singleton
  abstract fun bindVpnRepository(
    vpnRepositoryImpl: VpnRepositoryImpl
  ): VpnRepository

  @Binds
  @Singleton
  abstract fun bindVpnLifecycleController(
    vpnLifecycleControllerImpl: VpnLifecycleControllerImpl
  ): VpnLifecycleController

  @Binds
  @Singleton
  abstract fun bindTunnelCoordinator(
    tunnelCoordinatorImpl: TunnelCoordinatorImpl
  ): TunnelCoordinator

  companion object {
    @Provides
    @TunnelReleaseGrace
    fun provideTunnelReleaseGrace(): Long = TUNNEL_RELEASE_GRACE_MS
  }
}
