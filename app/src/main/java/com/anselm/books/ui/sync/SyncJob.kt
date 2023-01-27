package com.anselm.books.ui.sync

import android.util.Log
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.TAG
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

object MimeType {
    val APPLICATION_JSON = "application/json".toMediaType()
    val MULTIPART_RELATED = "multipart/related".toMediaType()
    const val APPLICATION_FOLDER = "application/vnd.google-apps.folder"
}

class SyncJob(
    private val authToken: String,
) {
    enum class Status {
        STARTED, FINISHED
    }
    private var status = Status.STARTED
    private val tag = nextTag()
    var isCancelled = false
        private set
    var exception: Exception? = null
        private set(value) {
            field = value
            lock.withLock {
                cond.signalAll()
            }
        }
    val requestCount = AtomicInteger(0)
    val finishedCount = AtomicInteger(0)

    private fun builder(): Request.Builder {

        return Request.Builder()
            .tag(tag)
            .header("Authorization", "Bearer $authToken")
    }

    fun createFolder(
        name: String,
        parentFolderId: String? = null,
        onResponse: (GoogleFile) -> Unit,
    ) {
        Log.d(TAG, "createFolder: $name, parent: $parentFolderId.")
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrlOrNull()!!.newBuilder()
        val metadata = GoogleFile(
            id = "",
            name = name,
            mimeType = MimeType.APPLICATION_FOLDER,
            folderId = parentFolderId
        ).toJson().toString()
        val req = builder()
            .url(url.build())
            .post(metadata.toRequestBody(MimeType.APPLICATION_JSON))
            .build()
        run(req) { onResponse(GoogleFile.fromJson(it)) }
    }

    fun uploadFile(
        file: File,
        mimeType: String,
        folderId: String? = null,
        onResponse: (GoogleFile) -> Unit,
    ) {
        Log.d(TAG, "uploadFile: ${file.name} type: $mimeType.")
        val url = "https://www.googleapis.com/upload/drive/v3/files".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("uploadType", "multipart")
        val metadata = GoogleFile("", file.name, mimeType, folderId).toJson().toString()
        val multipartBody = MultipartBody.Builder()
            .setType(MimeType.MULTIPART_RELATED)
            .addPart(metadata.toRequestBody(MimeType.APPLICATION_JSON))
            .addPart(file.asRequestBody(mimeType.toMediaType()))
            .build()
        val req = builder()
            .url(url.build())
            .header("Content-Length", multipartBody.contentLength().toString())
            .post(multipartBody)
            .build()
        run(req) { onResponse(GoogleFile.fromJson(it)) }
    }

    fun listFiles(
        onResponse: (List<GoogleFile>) -> Unit,
        pageToken: String? = null,
        into: MutableList<GoogleFile>? = null,
    ) {
        val files = into ?: mutableListOf()
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("q", "trashed = false")
            .addQueryParameter("fields", "nextPageToken, files(id, name, mimeType, parents)")
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("pageSize", "500")
        if (pageToken != null) {
            url.addQueryParameter("pageToken", pageToken)
        }
        val req = builder()
            .url(url.build())
            .build()
        return run(req) { obj ->
            obj.optJSONArray("files")?.let { jsFileArray ->
                (0 until jsFileArray.length()).map { position ->
                    val jsFile = jsFileArray.get(position) as JSONObject
                    files.add(GoogleFile.fromJson(jsFile))
                }
            }
            val nextToken = obj.optString("nextPageToken")
            if (nextToken.isEmpty()) {
                onResponse(files)
            } else {
                listFiles(onResponse, nextToken, files)
            }
        }
    }

    fun delete(fileId: String, onResponse: (JSONObject) -> Unit) {
        Log.d(TAG, "deleteFile: $fileId.")
        val url = "https://www.googleapis.com/drive/v3/files/$fileId"
        val req = builder()
            .url(url)
            .method("DELETE", null)
            .build()
        run(req) { onResponse(it) }
    }

    private fun parseJson(response: Response): JSONObject? {
        val text = response.body?.string()
        if (text != null && text.isNotEmpty()) {
            val obj = JSONTokener(text).nextValue()
            if (obj !is JSONObject) {
                Log.e(TAG, "${response.request.url}: parse failed got a ${obj.javaClass.name}.")
            } else {
                return obj
            }
        }
        return null
    }

    private fun run(req: Request, onResponse: (JSONObject) -> Unit) {
        if (isCancelled || (exception != null)) {
            Log.e(TAG, "SyncJob $tag cancelled/erred, rejecting request.")
            return
        }
        val url = req.url
        val call = app.okHttp.newCall(req)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                finishedCount.incrementAndGet()
                Log.e(TAG, "$url: request failed.", e)
                exception = e
            }

            override fun onResponse(call: Call, response: Response) {
                finishedCount.incrementAndGet()
                response.use {
                    try {
                        val obj = parseJson(response)
                        if (it.isSuccessful) {
                            onResponse(obj ?: JSONObject())
                        } else {
                            // Throw and catch: all errors match an exception.
                            Log.d(TAG, "$url: status ${response.code} body: $obj.")
                            throw SyncException("$url: request failed.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "$url: handling failed.", e)
                        exception = e
                    }
                }
            }
        })
        requestCount.incrementAndGet()
    }

    private val lock = ReentrantLock()
    private val cond = lock.newCondition()

    fun done() {
        lock.withLock {
            status = Status.FINISHED
            cond.signalAll()
        }
    }

    fun cancel() {
        lock.withLock {
            isCancelled = true
            cond.signalAll()
        }
        app.cancelHttpRequests(tag)
    }

    fun flush() {
        while (status != Status.FINISHED  && ! isCancelled && exception == null) {
            try {
                lock.withLock {
                    cond.await()
                }
            } catch (_: InterruptedException) { }
        }
        app.flushOkHttp()
    }

    fun start(runnable: Runnable) {
        thread {
            runnable.run()
        }
    }


    companion object {
        private val idCounter = AtomicInteger(0)
        private fun nextTag() = "syncjob-${idCounter.incrementAndGet()}"
    }
}