package com.gdisys.cameras.core.storage.domain.model

import com.gdisys.cameras.core.network.STREAM_URL_HOST_PREFIX

/** Factory configuration offered to the user before they have registered any URL. */
object StreamDefaults {

  private const val DEFAULT_PORT = 8889
  private val DEFAULT_CAMERA_IDS = 160..163

  // The host comes from STREAM_URL_HOST_PREFIX so that these defaults cannot drift from the host
  // allowed by network_security_config.
  val CAMERA_STREAM_URLS: List<String> = DEFAULT_CAMERA_IDS.map { cameraId ->
    "$STREAM_URL_HOST_PREFIX$DEFAULT_PORT/cam_$cameraId"
  }
}
