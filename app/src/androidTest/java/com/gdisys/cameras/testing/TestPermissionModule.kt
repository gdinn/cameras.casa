package com.gdisys.cameras.testing

import com.gdisys.cameras.core.permission.data.CameraPermissionRepositoryImpl
import com.gdisys.cameras.core.permission.di.PermissionModule
import com.gdisys.cameras.core.permission.domain.CameraPermissionRepository
import com.gdisys.cameras.core.permission.domain.VpnPermissionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces the VPN consent with [FakeVpnPermissionRepository] in every `@HiltAndroidTest`.
 *
 * The camera permission stays real: granting it with `UiAutomation` does not kill the process,
 * and its system dialog never opens once it is granted. The VPN consent has no such API (the app op
 * survives `pm clear`), so it is faked instead.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PermissionModule::class])
abstract class TestPermissionModule {

  @Binds
  @Singleton
  abstract fun bindCameraPermissionRepository(
    impl: CameraPermissionRepositoryImpl
  ): CameraPermissionRepository

  @Binds
  abstract fun bindVpnPermissionRepository(fake: FakeVpnPermissionRepository): VpnPermissionRepository
}
