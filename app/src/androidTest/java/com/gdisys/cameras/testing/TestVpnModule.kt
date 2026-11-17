package com.gdisys.cameras.testing

import com.gdisys.cameras.core.vpn.data.TunnelCoordinatorImpl
import com.gdisys.cameras.core.vpn.data.TunnelReleaseGrace
import com.gdisys.cameras.core.vpn.di.VpnModule
import com.gdisys.cameras.core.vpn.domain.TunnelCoordinator
import com.gdisys.cameras.core.vpn.domain.VpnLifecycleController
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces the WireGuard tunnel and its lifecycle service in every `@HiltAndroidTest`, so no test
 * brings up a real VPN. Tests inject [FakeVpnRepository] and [FakeVpnLifecycleController] directly
 * to drive and inspect them: being `@Singleton`, they are the same instances the app gets.
 *
 * The [TunnelCoordinator] is the production one, over the fakes, with no grace period: the tunnel
 * goes down as soon as the last holder leaves. A delay is invisible to Espresso's idling (see
 * `IdlingDispatcher`), so with one the flows would assert before the disconnect. The grace period
 * itself is covered by `TunnelCoordinatorImplTest`.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [VpnModule::class])
abstract class TestVpnModule {

  @Binds
  abstract fun bindVpnRepository(fake: FakeVpnRepository): VpnRepository

  @Binds
  abstract fun bindVpnLifecycleController(fake: FakeVpnLifecycleController): VpnLifecycleController

  @Binds
  @Singleton
  abstract fun bindTunnelCoordinator(impl: TunnelCoordinatorImpl): TunnelCoordinator

  companion object {
    @Provides
    @TunnelReleaseGrace
    fun provideNoGrace(): Long = 0L
  }
}
