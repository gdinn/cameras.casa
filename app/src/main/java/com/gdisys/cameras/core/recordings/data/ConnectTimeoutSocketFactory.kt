package com.gdisys.cameras.core.recordings.data

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

/**
 * smbj connects through `SocketFactory.createSocket(host, port)`, which has no timeout: with the
 * tunnel down, a connect to the Pi would wait for the system's TCP timeout (minutes). This factory
 * connects with [timeoutMillis] instead.
 */
internal class ConnectTimeoutSocketFactory(private val timeoutMillis: Int) : SocketFactory() {

  override fun createSocket(): Socket = Socket()

  override fun createSocket(host: String, port: Int): Socket =
    connected(InetSocketAddress(host, port), local = null)

  override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
    connected(InetSocketAddress(host, port), InetSocketAddress(localHost, localPort))

  override fun createSocket(host: InetAddress, port: Int): Socket =
    connected(InetSocketAddress(host, port), local = null)

  override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
    connected(InetSocketAddress(address, port), InetSocketAddress(localAddress, localPort))

  private fun connected(remote: SocketAddress, local: SocketAddress?): Socket {
    val socket = Socket()
    try {
      if (local != null) socket.bind(local)
      socket.connect(remote, timeoutMillis)
      return socket
    } catch (e: IOException) {
      socket.close()
      throw e
    }
  }
}
