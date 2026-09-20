package com.gdisys.cameras.core.network

/**
 * Single source of truth for the camera stream host.
 *
 * Every stream URL the app builds, validates or ships as a default resolves to this host, which is
 * also the only host allowed by `res/xml/network_security_config.xml`. A value declared in more
 * than one place would let a URL pass validation and then fail at runtime with a cleartext error,
 * so the constant lives here and everything else derives from it.
 *
 * The XML cannot reference a Kotlin constant, so `StreamHostTest` asserts the two stay in sync.
 */
const val STREAM_HOST = "[fd00:20::cafe]"

/**
 * Fixed prefix of every stream URL: scheme plus host, up to the port separator.
 *
 * It is also the label rendered before the port field in the "add URL" form, so the text on screen
 * cannot drift from the URL that is actually assembled.
 */
const val STREAM_URL_HOST_PREFIX = "http://$STREAM_HOST:"
