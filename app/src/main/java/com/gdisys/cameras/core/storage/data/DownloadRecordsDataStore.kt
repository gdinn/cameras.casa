package com.gdisys.cameras.core.storage.data

import android.content.Context
import androidx.datastore.dataStore

// Released values: renaming either one orphans every installed user's records.
internal const val DOWNLOAD_RECORDS_FILE_NAME = "download-records"
internal const val DOWNLOAD_RECORDS_KEY_ALIAS = "gdi-sys-download-records"

val Context.downloadRecordsDataStore by dataStore(
  fileName = DOWNLOAD_RECORDS_FILE_NAME,
  serializer = downloadRecordsSerializer(KeystoreCryptoEngine(DOWNLOAD_RECORDS_KEY_ALIAS))
)
