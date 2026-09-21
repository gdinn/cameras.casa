package com.gdisys.cameras.core.permission.di

import com.gdisys.cameras.core.permission.data.CameraPermissionRepositoryImpl
import com.gdisys.cameras.core.permission.data.VpnPermissionRepositoryImpl
import com.gdisys.cameras.core.permission.domain.CameraPermissionRepository
import com.gdisys.cameras.core.permission.domain.VpnPermissionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionModule {

  @Binds
  @Singleton
  abstract fun bindCameraPermissionRepository(
    cameraPermissionRepositoryImpl: CameraPermissionRepositoryImpl
  ): CameraPermissionRepository

  @Binds
  @Singleton
  abstract fun bindVpnPermissionRepository(
    vpnPermissionRepositoryImpl: VpnPermissionRepositoryImpl
  ): VpnPermissionRepository
}
