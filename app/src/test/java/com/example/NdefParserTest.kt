package com.example

import android.nfc.NdefRecord
import com.example.nfc.NdefParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NdefParserTest {

  @Test
  fun `parse text record correctly`() {
    val textRecord = NdefRecord.createTextRecord("es", "Hola ColmaApp")
    val parsed = NdefParser.parseRecord(textRecord)

    assertEquals("Texto", parsed.type)
    assertEquals("Hola ColmaApp", parsed.payload)
  }

  @Test
  fun `parse uri record correctly`() {
    val uriRecord = NdefRecord.createUri("https://colmaapp.ai.studio/")
    val parsed = NdefParser.parseRecord(uriRecord)

    assertEquals("URL/URI", parsed.type)
    assertEquals("https://colmaapp.ai.studio/", parsed.payload)
  }

  @Test
  fun `parse mime record correctly`() {
    val payloadBytes = "{\"cardId\":\"CU-27001-161001\"}".toByteArray(Charsets.UTF_8)
    val mimeRecord = NdefRecord.createMime("application/json", payloadBytes)
    val parsed = NdefParser.parseRecord(mimeRecord)

    assertTrue(parsed.type.contains("MIME"))
    assertEquals("{\"cardId\":\"CU-27001-161001\"}", parsed.payload)
  }
}
