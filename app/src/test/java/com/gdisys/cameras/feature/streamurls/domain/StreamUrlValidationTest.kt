package com.gdisys.cameras.feature.streamurls.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamUrlValidationTest {

  @Test
  fun `valid port and stream name build the full url`() {
    val result = validateStreamUrl(port = "8889", streamName = "cam_160", existingUrls = emptyList())

    assertEquals(
      StreamUrlValidationResult.Valid("http://[fd00:20::cafe]:8889/cam_160"),
      result
    )
  }

  @Test
  fun `port accepts the boundaries of the allowed range`() {
    assertEquals(
      StreamUrlValidationResult.Valid("http://[fd00:20::cafe]:1/cam"),
      validateStreamUrl(port = "1", streamName = "cam", existingUrls = emptyList())
    )
    assertEquals(
      StreamUrlValidationResult.Valid("http://[fd00:20::cafe]:65535/cam"),
      validateStreamUrl(port = "65535", streamName = "cam", existingUrls = emptyList())
    )
  }

  @Test
  fun `port outside the allowed range is invalid`() {
    assertEquals(
      StreamUrlValidationResult.InvalidPort,
      validateStreamUrl(port = "0", streamName = "cam", existingUrls = emptyList())
    )
    assertEquals(
      StreamUrlValidationResult.InvalidPort,
      validateStreamUrl(port = "65536", streamName = "cam", existingUrls = emptyList())
    )
    assertEquals(
      StreamUrlValidationResult.InvalidPort,
      validateStreamUrl(port = "-1", streamName = "cam", existingUrls = emptyList())
    )
  }

  @Test
  fun `empty or non numeric port is invalid`() {
    assertEquals(
      StreamUrlValidationResult.InvalidPort,
      validateStreamUrl(port = "", streamName = "cam", existingUrls = emptyList())
    )
    assertEquals(
      StreamUrlValidationResult.InvalidPort,
      validateStreamUrl(port = "88a9", streamName = "cam", existingUrls = emptyList())
    )
    assertEquals(
      StreamUrlValidationResult.InvalidPort,
      validateStreamUrl(port = "88.9", streamName = "cam", existingUrls = emptyList())
    )
  }

  @Test
  fun `port is normalized to its numeric form`() {
    assertEquals(
      StreamUrlValidationResult.Valid("http://[fd00:20::cafe]:80/cam"),
      validateStreamUrl(port = "0080", streamName = "cam", existingUrls = emptyList())
    )
  }

  @Test
  fun `stream name accepts letters digits underscore and hyphen`() {
    assertEquals(
      StreamUrlValidationResult.Valid("http://[fd00:20::cafe]:8889/Cam-160_HD"),
      validateStreamUrl(port = "8889", streamName = "Cam-160_HD", existingUrls = emptyList())
    )
  }

  @Test
  fun `empty stream name is invalid`() {
    assertEquals(
      StreamUrlValidationResult.InvalidStreamName,
      validateStreamUrl(port = "8889", streamName = "", existingUrls = emptyList())
    )
  }

  @Test
  fun `stream name with unsupported characters is invalid`() {
    listOf("cam 160", "cam/160", "cam.160", "câm", "cam?x=1").forEach { streamName ->
      assertEquals(
        "expected $streamName to be rejected",
        StreamUrlValidationResult.InvalidStreamName,
        validateStreamUrl(port = "8889", streamName = streamName, existingUrls = emptyList())
      )
    }
  }

  @Test
  fun `stream name case is preserved in the built url`() {
    assertEquals(
      StreamUrlValidationResult.Valid("http://[fd00:20::cafe]:8889/CaM_160"),
      validateStreamUrl(port = "8889", streamName = "CaM_160", existingUrls = emptyList())
    )
  }

  @Test
  fun `duplicate url is rejected regardless of case`() {
    val existingUrls = listOf("http://[fd00:20::cafe]:8889/cam_160")

    assertEquals(
      StreamUrlValidationResult.DuplicateUrl,
      validateStreamUrl(port = "8889", streamName = "CAM_160", existingUrls = existingUrls)
    )
  }

  @Test
  fun `a url that differs only by port is not a duplicate`() {
    val existingUrls = listOf("http://[fd00:20::cafe]:8889/cam_160")

    assertEquals(
      StreamUrlValidationResult.Valid("http://[fd00:20::cafe]:8890/cam_160"),
      validateStreamUrl(port = "8890", streamName = "cam_160", existingUrls = existingUrls)
    )
  }

  @Test
  fun `port is validated before the stream name`() {
    assertEquals(
      StreamUrlValidationResult.InvalidPort,
      validateStreamUrl(port = "0", streamName = "", existingUrls = emptyList())
    )
  }
}
