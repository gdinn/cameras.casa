package com.gdisys.cameras.core.recordings.data

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

class ConnectTimeoutSocketFactoryTest {

  private val loopback = InetAddress.getLoopbackAddress()
  private val loopbackHost = requireNotNull(loopback.hostAddress)
  private val server = ServerSocket(0, 50, loopback)
  private val factory = ConnectTimeoutSocketFactory(timeoutMillis = 2_000)

  @After
  fun tearDown() = server.close()

  @Test
  fun `every variant returns a connected socket`() {
    listOf(
      factory.createSocket(loopbackHost, server.localPort),
      factory.createSocket(loopback, server.localPort),
      factory.createSocket(loopbackHost, server.localPort, loopback, 0),
      factory.createSocket(loopback, server.localPort, loopback, 0)
    ).forEach { socket ->
      socket.use { assertTrue(it.isConnected) }
    }
  }

  @Test
  fun `the plain variant is left unconnected`() {
    factory.createSocket().use { assertFalse(it.isConnected) }
  }

  @Test
  fun `a refused connection throws`() {
    val port = server.localPort
    server.close()

    val thrown = runCatching { factory.createSocket(loopback, port) }.exceptionOrNull()

    assertTrue(thrown is IOException)
  }
}
