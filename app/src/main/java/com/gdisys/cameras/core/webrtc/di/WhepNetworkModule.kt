package com.gdisys.cameras.core.webrtc.di

import com.gdisys.cameras.core.webrtc.data.WhepClientImpl
import com.gdisys.cameras.core.webrtc.data.remote.WhepRemoteDataSource
import com.gdisys.cameras.core.webrtc.data.remote.WhepRemoteDataSourceImpl
import com.gdisys.cameras.core.webrtc.WhepClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.components.ViewModelComponent

/**
 * Provides the network DataSource. It can be a Singleton because it holds no state.
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
 * Provides the WebRTC client.
 *
 * Bound to [ViewModelComponent] because the client holds state (a mutable `peerConnection`), so
 * each ViewModel gets its own instance and manages its connection independently.
 */
@Module
@InstallIn(ViewModelComponent::class)
abstract class WhepClientModule {

  @Binds
  abstract fun bindWhepClient(
    impl: WhepClientImpl
  ): WhepClient
}