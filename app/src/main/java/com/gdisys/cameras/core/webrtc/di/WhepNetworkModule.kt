package com.gdisys.cameras.core.webrtc.di

import com.gdisys.cameras.core.webrtc.data.WhepClientImpl
import com.gdisys.cameras.core.webrtc.data.remote.WhepRemoteDataSource
import com.gdisys.cameras.core.webrtc.data.remote.WhepRemoteDataSourceImpl
import com.gdisys.cameras.core.webrtc.domain.WhepClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.components.ViewModelComponent

/**
 * Módulo para prover o DataSource de rede. Pode ser Singleton, pois não guarda estado.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WhepNetworkModule {

  @Binds
  abstract fun bindWhepRemoteDataSource(
    impl: WhepRemoteDataSourceImpl
  ): WhepRemoteDataSource
}

/**
 * Módulo para prover o Cliente WebRTC.
 *
 * Usamos [ViewModelComponent] porque o Client guarda estado (var peerConnection).
 * Dessa forma, cada ViewModel pode injetar uma nova instância para gerenciar
 * sua própria conexão de forma independente.
 */
@Module
@InstallIn(ViewModelComponent::class)
abstract class WhepClientModule {

  @Binds
  abstract fun bindWhepClient(
    impl: WhepClientImpl
  ): WhepClient
}