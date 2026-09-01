package com.gdisys.cameras.core.webrtc.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

private const val HTTP_TIMEOUT_MS = 10_000
private const val HTTP_CREATED = 201

interface WhepRemoteDataSource {
  suspend fun postOffer(streamUrl: String, offerSdp: String): String
}

class WhepRemoteDataSourceImpl @Inject constructor() : WhepRemoteDataSource {

  override suspend fun postOffer(streamUrl: String, offerSdp: String): String =
    withContext(Dispatchers.IO) {
      val connection = URL("$streamUrl/whep").openConnection() as HttpURLConnection
      try {
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/sdp")
        connection.connectTimeout = HTTP_TIMEOUT_MS
        connection.readTimeout = HTTP_TIMEOUT_MS
        connection.doOutput = true
        connection.outputStream.use { it.write(offerSdp.toByteArray()) }

        check(connection.responseCode == HTTP_CREATED) {
          "Servidor WHEP retornou ${connection.responseCode} para $streamUrl"
        }
        connection.inputStream.bufferedReader().readText()
      } finally {
        connection.disconnect()
      }
    }
}