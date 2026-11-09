package com.gdisys.cameras.testing

import com.gdisys.cameras.core.vpn.di.VpnModule
import com.gdisys.cameras.core.vpn.domain.VpnLifecycleController
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/**
 * Replaces the WireGuard tunnel and its lifecycle service in every `@HiltAndroidTest`, so no test
 * brings up a real VPN. Tests inject [FakeVpnRepository] and [FakeVpnLifecycleController] directly
 * to drive and inspect them: being `@Singleton`, they are the same instances the app gets.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [VpnModule::class])
abstract class TestVpnModule {

  @Binds
  abstract fun bindVpnRepository(fake: FakeVpnRepository): VpnRepository

  @Binds
  abstract fun bindVpnLifecycleController(fake: FakeVpnLifecycleController): VpnLifecycleController
}
