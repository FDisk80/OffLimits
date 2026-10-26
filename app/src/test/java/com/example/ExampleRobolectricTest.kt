package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("OffLimits", appName)
  }

  @Test
  fun `speed unit conversions are accurate`() {
    val speedKmh = 100.0
    val speedMph = speedKmh * SpeedUnit.MPH.speedMultiplier
    assertEquals(62.1371, speedMph, 0.01)
  }
}
