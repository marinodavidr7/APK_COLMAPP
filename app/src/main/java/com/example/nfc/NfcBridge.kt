package com.example.nfc

import android.content.Context
import android.nfc.NfcAdapter
import android.webkit.JavascriptInterface

class NfcBridge(private val context: Context) {

  private val nfcAdapter: NfcAdapter? by lazy {
    try {
      NfcAdapter.getDefaultAdapter(context)
    } catch (e: Exception) {
      null
    }
  }

  @Volatile
  var lastTagData: NfcTagData? = null

  @JavascriptInterface
  fun isNfcAvailable(): Boolean {
    return nfcAdapter != null
  }

  @JavascriptInterface
  fun isNfcEnabled(): Boolean {
    return nfcAdapter?.isEnabled == true
  }

  @JavascriptInterface
  fun getCardPayload(): String {
    return "CU-27001-161001"
  }

  @JavascriptInterface
  fun getLastTagId(): String {
    return lastTagData?.tagId ?: ""
  }

  @JavascriptInterface
  fun getLastTagPayload(): String {
    return lastTagData?.primaryText ?: lastTagData?.primaryUri ?: ""
  }

  @JavascriptInterface
  fun hasScannedTag(): Boolean {
    return lastTagData != null
  }

  @JavascriptInterface
  fun clearLastTag() {
    lastTagData = null
  }
}
