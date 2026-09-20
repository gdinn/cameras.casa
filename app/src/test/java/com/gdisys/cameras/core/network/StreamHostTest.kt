package com.gdisys.cameras.core.network

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the one link the compiler cannot see.
 *
 * [STREAM_HOST] is the host every stream URL resolves to, but cleartext traffic to it is only
 * allowed because `network_security_config.xml` declares the very same host. The XML cannot
 * reference the Kotlin constant, so changing one without the other compiles and passes every other
 * test, and only fails on device with a cleartext error. This test turns that into a build failure.
 */
class StreamHostTest {

  private val networkSecurityConfig = File("src/main/res/xml/network_security_config.xml")

  @Test
  fun `network security config allows the stream host`() {
    assertTrue(
      "network_security_config.xml not found at ${networkSecurityConfig.absolutePath}",
      networkSecurityConfig.isFile
    )

    val declaredDomains = DOMAIN_REGEX.findAll(networkSecurityConfig.readText())
      .map { it.groupValues[1].trim() }
      .toList()

    assertTrue(
      "network_security_config.xml must declare $STREAM_HOST as a cleartext domain, " +
        "but only declares $declaredDomains",
      STREAM_HOST in declaredDomains
    )
  }

  @Test
  fun `stream url prefix is built from the stream host`() {
    assertTrue(STREAM_URL_HOST_PREFIX.contains(STREAM_HOST))
  }

  private companion object {
    val DOMAIN_REGEX = Regex("<domain[^>]*>([^<]+)</domain>")
  }
}
