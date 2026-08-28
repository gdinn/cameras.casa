package com.gdisys.cameras.feature.cameras.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

class PeerConnectionFactoryHolder(
  val factory: PeerConnectionFactory,
  val eglBase: EglBase,
) {
  fun dispose() {
    factory.dispose()
    eglBase.release()
  }
}

@Composable
fun rememberPeerConnectionFactory(): PeerConnectionFactoryHolder {
  val context = LocalContext.current

  val holder = remember {
    val eglBase = EglBase.create()

    PeerConnectionFactory.initialize(
      PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
        .setEnableInternalTracer(false)
        .createInitializationOptions()
    )
    val factory = PeerConnectionFactory.builder()
      .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
      .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
      .createPeerConnectionFactory()

    PeerConnectionFactoryHolder(factory, eglBase)
  }

  DisposableEffect(holder) {
    onDispose { holder.dispose() }
  }

  return holder
}
