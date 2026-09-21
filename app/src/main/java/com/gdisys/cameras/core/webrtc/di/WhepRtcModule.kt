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
object WhepRtcModule {

  /**
   * Provides one application-wide EglBase, so the UI and the WebRTC stack share the same EGL
   * context — the renderer and the decoder have to agree on it.
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
    // 1. WebRTC requires global initialization before the factory can be created.
    PeerConnectionFactory.initialize(
      PeerConnectionFactory.InitializationOptions.builder(context)
        .setEnableInternalTracer(true)
        .createInitializationOptions()
    )

    // 2. Build the factory on the EglBase provided above.
    return PeerConnectionFactory.builder()
      .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
      .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
      .createPeerConnectionFactory()
  }
}