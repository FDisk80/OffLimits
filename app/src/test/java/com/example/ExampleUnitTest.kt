package com.example

import com.example.data.network.OsmSpeedService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testCardinalDirections() {
    val service = OsmSpeedService()
    assertEquals("N", service.getCardinalDirection(0f))
    assertEquals("N", service.getCardinalDirection(350f))
    assertEquals("NE", service.getCardinalDirection(45f))
    assertEquals("E", service.getCardinalDirection(90f))
    assertEquals("SE", service.getCardinalDirection(135f))
    assertEquals("S", service.getCardinalDirection(180f))
    assertEquals("SW", service.getCardinalDirection(225f))
    assertEquals("W", service.getCardinalDirection(270f))
    assertEquals("NW", service.getCardinalDirection(315f))
  }
}

