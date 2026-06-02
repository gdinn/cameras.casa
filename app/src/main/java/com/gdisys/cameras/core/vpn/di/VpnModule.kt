package com.gdisys.cameras.core.vpn.di

import com.gdisys.cameras.core.vpn.data.VpnRepositoryImpl
import com.gdisys.cameras.core.vpn.domain.VpnRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VpnModule {

  @Binds
  @Singleton
  abstract fun bindVpnRepository(
    vpnRepositoryImpl: VpnRepositoryImpl
  ): VpnRepository
}
