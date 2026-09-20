package com.gdisys.cameras.core.storage.domain.model

/** Configuração de fábrica oferecida ao usuário quando ele ainda não cadastrou URLs. */
object StreamDefaults {

  // http://[fd00:20::cafe] é o host padrão por conta do network_security_config
  val CAMERA_STREAM_URLS = listOf(
    "http://[fd00:20::cafe]:8889/cam_160",
    "http://[fd00:20::cafe]:8889/cam_161",
    "http://[fd00:20::cafe]:8889/cam_162",
    "http://[fd00:20::cafe]:8889/cam_163"
  )
}
