package com.anselm.books.ui.sync

import android.util.Log
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.TAG
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.Instant

class SyncConfig(
    fileName: String = "sync-config.json"
) {
    private val file = File(app.applicationContext?.filesDir, fileName)
    var folderId: String = ""
    var jsonFileId: String = ""
    var lastSync: Long = -1

    init {
        load()
    }

    fun save(updateLastSync: Boolean = false) {
        if (updateLastSync) {
            lastSync = Instant.now().toEpochMilli()
        }
        try {
            val obj = JSONObject()
            obj.put("folderId", folderId)
            obj.put("jsonFileId", jsonFileId)
            obj.put("lastSyncDate", lastSync)
            file.outputStream().use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use {
                    it.write(obj.toString(2))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save lookup statistics from ${file.path} (ignored)", e)
        }
    }

    private fun load() {
        try {
            var obj: JSONObject
            file.inputStream().use { inputStream ->
                InputStreamReader(inputStream).use {
                    obj = JSONTokener(it.readText()).nextValue() as JSONObject
                }
            }
            folderId = obj.optString("folderId")
            jsonFileId = obj.optString("jsonFileId")
            lastSync = obj.getLong("lastSyncDate")
        } catch (e: Exception) {
            // By deleting the file we make sure it'll get recreated properly next time around.
            Log.e(TAG, "Failed to load lookup statistics from ${file.path} (ignored)", e)
            file.delete()
            save()
        }
    }

    companion object {
        private var config: SyncConfig? = null

        @Synchronized
        fun get(): SyncConfig {
            if (config == null) {
                config = SyncConfig()
            }
            return config!!
        }
    }
}