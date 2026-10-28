package com.example.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import java.nio.charset.Charset

object NdefParser {

  fun parseTag(tag: Tag): NfcTagData {
    val tagIdHex = tag.id?.joinToString(":") { "%02X".format(it) } ?: "Desconocido"
    val techList = tag.techList.map { it.substringAfterLast(".") }

    val ndef = Ndef.get(tag)
    if (ndef == null) {
      return NfcTagData(
        tagId = tagIdHex,
        techList = techList,
        isNdef = false,
        primaryText = "Etiqueta NFC compatible (UID: $tagIdHex)"
      )
    }

    var isWritable = false
    var maxSize = 0
    var message: NdefMessage? = null

    try {
      ndef.connect()
      isWritable = ndef.isWritable
      maxSize = ndef.maxSize
      message = ndef.ndefMessage ?: ndef.cachedNdefMessage
    } catch (e: Exception) {
      message = ndef.cachedNdefMessage
    } finally {
      try {
        ndef.close()
      } catch (ignored: Exception) {}
    }

    val records = mutableListOf<NfcRecordData>()
    var primaryText: String? = null
    var primaryUri: String? = null

    if (message != null) {
      for (record in message.records) {
        val parsed = parseRecord(record)
        records.add(parsed)
        if (primaryText == null && parsed.type == "Texto") {
          primaryText = parsed.payload
        }
        if (primaryUri == null && parsed.type == "URL/URI") {
          primaryUri = parsed.payload
        }
      }
    }

    return NfcTagData(
      tagId = tagIdHex,
      techList = techList,
      isNdef = true,
      isWritable = isWritable,
      maxSize = maxSize,
      records = records,
      primaryText = primaryText ?: primaryUri ?: "Etiqueta NDEF leída (sin texto directo)",
      primaryUri = primaryUri
    )
  }

  fun parseRecord(record: NdefRecord): NfcRecordData {
    val tnf = record.tnf
    val type = record.type
    val payload = record.payload

    return when {
      tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_TEXT) -> {
        val text = parseTextRecord(payload)
        NfcRecordData(type = "Texto", payload = text, mimeOrType = "text/plain")
      }
      tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_URI) -> {
        val uri = parseUriRecord(payload)
        NfcRecordData(type = "URL/URI", payload = uri, mimeOrType = "URI")
      }
      tnf == NdefRecord.TNF_MIME_MEDIA -> {
        val mimeType = String(type, Charsets.US_ASCII)
        val text = String(payload, Charsets.UTF_8)
        NfcRecordData(type = "MIME ($mimeType)", payload = text, mimeOrType = mimeType)
      }
      else -> {
        val raw = try {
          String(payload, Charsets.UTF_8).filter { it.isLetterOrDigit() || it.isWhitespace() || "!@#$%^&*()_+-=[]{}|;':,.<>/?".contains(it) }
        } catch (e: Exception) {
          payload.joinToString(" ") { "%02X".format(it) }
        }
        NfcRecordData(
          type = "Registro NDEF (TNF $tnf)",
          payload = raw.ifBlank { payload.joinToString(" ") { "%02X".format(it) } },
          mimeOrType = "TNF: $tnf"
        )
      }
    }
  }

  private fun parseTextRecord(payload: ByteArray): String {
    if (payload.isEmpty()) return ""
    val status = payload[0].toInt()
    val isUtf8 = (status and 0x80) == 0
    val langCodeLength = status and 0x3F
    val textOffset = 1 + langCodeLength
    if (textOffset > payload.size) return ""
    val charset = if (isUtf8) Charsets.UTF_8 else Charset.forName("UTF-16")
    return String(payload, textOffset, payload.size - textOffset, charset)
  }

  private val URI_PREFIX_MAP = listOf(
    "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
    "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://",
    "nfs://", "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://",
    "urn:", "pop:", "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://",
    "tcpobex://", "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:",
    "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:"
  )

  private fun parseUriRecord(payload: ByteArray): String {
    if (payload.isEmpty()) return ""
    val prefixIndex = payload[0].toInt() and 0xFF
    val prefix = if (prefixIndex in URI_PREFIX_MAP.indices) URI_PREFIX_MAP[prefixIndex] else ""
    val uriBody = String(payload, 1, payload.size - 1, Charsets.UTF_8)
    return prefix + uriBody
  }
}
