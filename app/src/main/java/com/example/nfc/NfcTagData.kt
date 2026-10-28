package com.example.nfc

data class NfcTagData(
  val tagId: String,
  val techList: List<String>,
  val isNdef: Boolean,
  val isWritable: Boolean = false,
  val maxSize: Int = 0,
  val records: List<NfcRecordData> = emptyList(),
  val primaryText: String? = null,
  val primaryUri: String? = null,
  val timestamp: Long = System.currentTimeMillis()
)

data class NfcRecordData(
  val type: String,
  val payload: String,
  val mimeOrType: String? = null
)
