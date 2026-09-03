package com.gdisys.cameras.core.webrtc.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WhepRemoteDataSourceImplTest {

  private val server = MockWebServer()
  private val dataSource = WhepRemoteDataSourceImpl()

  @Before
  fun setUp() {
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `postOffer sends the SDP offer and returns the SDP answer body on 201`() = runTest {
    server.enqueue(MockResponse().setResponseCode(201).setBody("v=0\r\nanswer-sdp"))
    val streamUrl = server.url("").toString().removeSuffix("/")

    val answer = dataSource.postOffer(streamUrl, "v=0\r\noffer-sdp")

    assertEquals("v=0\r\nanswer-sdp", answer)

    val request = server.takeRequest()
    assertEquals("POST", request.method)
    assertEquals("/whep", request.path)
    assertEquals("application/sdp", request.getHeader("Content-Type"))
    assertEquals("v=0\r\noffer-sdp", request.body.readUtf8())
  }

  @Test
  fun `postOffer throws when the server does not return 201`() = runTest {
    server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
    val streamUrl = server.url("").toString().removeSuffix("/")

    val error = runCatching { dataSource.postOffer(streamUrl, "v=0\r\noffer-sdp") }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
    assertTrue(error!!.message!!.contains("500"))
    assertTrue(error.message!!.contains(streamUrl))
  }
}
