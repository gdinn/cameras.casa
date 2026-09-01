package com.gdisys.cameras.core.permission.di

import com.gdisys.cameras.core.permission.data.CameraPermissionRepositoryImpl
import com.gdisys.cameras.core.permission.domain.CameraPermissionRepository
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
}
