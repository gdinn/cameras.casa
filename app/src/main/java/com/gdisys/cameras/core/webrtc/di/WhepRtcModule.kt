package com.gdisys.cameras.core.webrtc.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WebRtcModule {

  /**
   * Provê uma instância única (Singleton) do EglBase para toda a aplicação.
   * Assim você pode injetar o mesmo EglBase no ViewModel/View e no WebRTC.
   */
  @Provides
  @Singleton
  fun provideEglBase(): EglBase {
    return EglBase.create()
  }

  /**
   * Ensina o Hilt a criar o PeerConnectionFactory.
   */
  @Provides
  @Singleton
  fun providePeerConnectionFactory(
    @ApplicationContext context: Context,
    eglBase: EglBase
  ): PeerConnectionFactory {
    // 1. O WebRTC exige inicialização global antes de criar a Factory
    PeerConnectionFactory.initialize(
      PeerConnectionFactory.InitializationOptions.builder(context)
        .setEnableInternalTracer(true)
        .createInitializationOptions()
    )

    // 2. Constrói e retorna a Factory usando o EglBase provido acima
    return PeerConnectionFactory.builder()
      .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
      .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
      .createPeerConnectionFactory()
  }
}