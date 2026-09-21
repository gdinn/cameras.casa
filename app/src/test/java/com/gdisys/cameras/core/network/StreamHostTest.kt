package com.gdisys.cameras.core.network

import org.junit.Assert.assertEquals
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
  fun `network security config allows the stream host and nothing else`() {
    assertTrue(
      "network_security_config.xml not found at ${networkSecurityConfig.absolutePath}",
      networkSecurityConfig.isFile
    )

    val declaredDomains = DOMAIN_REGEX.findAll(networkSecurityConfig.readText())
      .map { it.groupValues[1].trim() }
      .toList()

    // Equality, not containment: every cleartext domain must derive from STREAM_HOST. Asserting
    // only that the host is present lets a stale extra domain survive forever, which is what makes
    // StreamHost.kt's claim that it is "the only host allowed" true by convention instead of by
    // construction. Cleartext is an exception granted to one host, so the list is the whitelist.
    assertEquals(
      "network_security_config.xml must declare exactly one cleartext domain, $STREAM_HOST. " +
        "Every host permitted here has to come from that constant.",
      listOf(STREAM_HOST),
      declaredDomains
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
