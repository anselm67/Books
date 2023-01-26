package com.anselm.books.ui.sync

/*
* ok http multipart
* https://gist.github.com/balvinder294/e869944161cb0af250b1296f64e3129a#file-post-file-java
* Bearer from drive api
Bearer ya29.a0AVvZVspof_MqeP87fIunVZ200zXfxd3I2J7rSmIQhTlWVk4I07u6cik2bDyGS-KnIoCeTefCdJcJ2gHMw9L5B859z5D1NpT_H8snfuogpfOeMQzaE6nHPTK--N8qK7DyCai8aA7Jhrx0wwCW_bO_E3R5piKslgaCgYKAeYSARISFQGbdwaIg8GKP-WnzEVjRzUP0d2I5w0165
 */
import android.util.Log
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.Constants
import com.anselm.books.TAG
import com.anselm.books.ifNotEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException


data class GoogleFile(
    val id: String,
    val name: String,
    val mimeType: String? = null,
    val folderId: String? = null,
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        id.ifNotEmpty {  obj.put("id", id) }
        name.ifNotEmpty { obj.put("name", name) }
        name.ifNotEmpty { obj.put("mimeType", mimeType) }
        if (folderId != null) {
            val parents = JSONArray().apply {
                put(folderId)
            }
            obj.put("parents", parents)
        }
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): GoogleFile {
            val parents = obj.optJSONArray("parents")
            var folderId: String? = null
            if (parents != null && parents.length() > 0) {
                folderId = parents.getString(0)
            }
            return GoogleFile(
                obj.optString("id"),
                obj.optString("name"),
                obj.optString("mimeType"),
                folderId,
            )
        }
    }
}

private class Node(
    val localDirectory: File,
    val localName: String,
) {
    var folderId: String? = null
    val localFiles = emptyList<String>().toMutableList()
    val remoteFiles = emptyList<GoogleFile>().toMutableList()
    val localChildren = emptyList<Node>().toMutableList()

    private fun getChild(name: String): Node? {
        return localChildren.firstOrNull { name == it.localName }
    }

    private fun diffChildren(drive: SyncDrive) {
        check(folderId != null)
        remoteFiles.forEach {
            if ( ! localFiles.contains(it.name)) {
                Log.d(TAG, "FETCH $localName/$it")
            }
        }
        localFiles.forEach { name ->
            if ( remoteFiles.firstOrNull{ it.name == name } == null ) {
                drive.uploadFile(File(localDirectory, name), "image/heic", folderId) {
                    Log.d(TAG, "$localName/$name uploaded.")
                }
            }
        }
        localChildren.forEach { it.diff(drive, folderId) }
    }

    fun diff(
        drive: SyncDrive,
        parentFolderId: String? = null,
    ) {
        if (folderId == null) {
            require(parentFolderId != null) { "parentFolderId required to createFolder." }
            drive.createFolder(localName, parentFolderId) {
                folderId = it.id
                diffChildren(drive)
            }
        } else {
            diffChildren(drive)
        }
    }

    private fun collectChildren(list: List<GoogleFile>) {
        list.forEach {
            if (it.folderId == folderId) {
                if (it.mimeType == SyncDrive.MimeType.APPLICATION_FOLDER) {
                    var localChild = getChild(it.name)
                    if (localChild != null) {
                        localChild.folderId = it.id
                    } else {
                        Log.d(TAG, "MKDIR $localName/${it.name}")
                        localChild = Node(File(localDirectory, it.name), it.name)
                        localChild.folderId = it.id
                    }
                    localChild.collectChildren(list)
                } else {
                    remoteFiles.add(it)
                }
            }
        }
    }

    fun merge(list: List<GoogleFile>): Node {
        // Find the root from our config.
        val root = list.firstOrNull { it.id == SyncConfig.get().folderId }
        if (root == null) {
            Log.d(TAG, "Root not matching, nothing we can do!")
            return this
        }
        folderId = root.id
        Log.d(TAG, "Merging $localName with ${root.name}")
        collectChildren(list)
        return this
    }

    private fun space(len: Int): String {
        return "                                                  ".substring(0, len)
    }

    fun display(level: Int = 0) {
        Log.d(TAG, "${space(level)}DIR: $localName")
        localFiles.forEach {
            Log.d(TAG, "${space(level+2)}CHILD: $it")
        }
        localChildren.forEach {
            it.display(level + 4)
        }
    }

    companion object {
        fun fromFile(root: File, into: Node? = null): Node {
            val node = into ?: Node(root, root.name)
            root.list()?.forEach {
                val childFile = File(root, it)
                if (childFile.isDirectory) {
                    val child = Node(childFile, it)
                    node.localChildren.add(child)
                    fromFile(childFile, child)
                } else {
                    node.localFiles.add(it)
                }
            }
            return node
        }
    }
}

class SyncDrive(
    private val authToken: String,
) {
    private val config = SyncConfig.get()

    object MimeType {
        val APPLICATION_JSON = "application/json".toMediaType()
        val MULTIPART_RELATED = "multipart/related".toMediaType()
        const val APPLICATION_FOLDER = "application/vnd.google-apps.folder"
    }

    fun createFolder(
        name: String,
        parentFolderId: String? = null,
        onResponse: (GoogleFile) -> Unit,
    ): Call {
        Log.d(TAG, "createFolder: $name, parent: $parentFolderId.")
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrlOrNull()!!.newBuilder()
        val metadata = GoogleFile(
            id = "",
            name = name,
            mimeType = MimeType.APPLICATION_FOLDER,
            folderId = parentFolderId
        ).toJson().toString()
        val req = Request.Builder()
            .url(url.build())
            .header("Authorization", "Bearer $authToken")
            .post(metadata.toRequestBody(MimeType.APPLICATION_JSON))
            .build()
        return run(req) { onResponse(GoogleFile.fromJson(it)) }
    }

    fun uploadFile(
        file: File,
        mimeType: String,
        folderId: String? = null,
        onResponse: (GoogleFile) -> Unit,
    ): Call {
        Log.d(TAG, "uploadFile: ${file.name} type: $mimeType.")
        val url = "https://www.googleapis.com/upload/drive/v3/files".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("uploadType", "multipart")
        val metadata = GoogleFile("", file.name, mimeType, folderId).toJson().toString()
        val multipartBody = MultipartBody.Builder()
            .setType(MimeType.MULTIPART_RELATED)
            .addPart(metadata.toRequestBody(MimeType.APPLICATION_JSON))
            .addPart(file.asRequestBody(mimeType.toMediaType()))
            .build()
        val req = Request.Builder()
            .url(url.build())
            .header("Authorization", "Bearer $authToken")
            .header("Content-Length", multipartBody.contentLength().toString())
            .post(multipartBody)
            .build()
        return run(req) { onResponse(GoogleFile.fromJson(it)) }
    }

    private fun listFiles(
        onResponse: (List<GoogleFile>) -> Unit,
        pageToken: String? = null,
        into: MutableList<GoogleFile>? = null,
    ) : Call {
        val files = into ?: mutableListOf()
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("q", "trashed = false")
            .addQueryParameter("fields", "nextPageToken, files(id, name, mimeType, parents)")
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("pageSize", "500")
        if (pageToken != null) {
            url.addQueryParameter("pageToken", pageToken)
        }
        return run(Request.Builder()
            .url(url.build())
            .header("Authorization", "Bearer $authToken")
            .build()) { obj ->
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

    private fun delete(fileId: String, onResponse: (JSONObject) -> Unit): Call {
        Log.d(TAG, "deleteFile: $fileId.")
        val url = "https://www.googleapis.com/drive/v3/files/$fileId"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $authToken")
            .method("DELETE", null)
            .build()
        return run(req) { onResponse(it) }
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

    private fun run(req: Request, onResponse: (JSONObject) -> Unit): Call {
        val url = req.url
        val call = app.okHttp.newCall(req)
        call.enqueue(object: Callback{
            override fun onFailure(call: Call, e: IOException) {
                // FIXME
                Log.e(TAG, "${call.request().url}: request failed.", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (it.isSuccessful) {
                            val obj = parseJson(it)
                            onResponse(obj ?: JSONObject())
                        } else {
                            Log.d(TAG, "$url: status ${response.code}.")
                            val obj = parseJson(response)
                            Log.e(TAG, "$url: error body $obj.")
                        }
                    } catch (e: Exception) {
                        // FIXME
                        Log.e(TAG, "$url: handling failed.", e)
                    }
                }
            }
        })
        return call
    }

    private fun createRoot(onDone: () -> Unit) {
        if (config.folderId.isEmpty()) {
            createFolder(Constants.DRIVE_FOLDER_NAME) {
                config.folderId = it.id
                onDone()
            }
        } else {
            onDone()
        }
    }

    private suspend fun syncJson(onDone: () -> Unit) {
        val file = File(app.applicationContext.cacheDir, "books.json")
        file.deleteOnExit()
        file.outputStream().use {
            app.importExport.exportJson(it)
        }
        if (config.jsonFileId.isNotEmpty()) {
            delete(config.jsonFileId) {
                Log.d(TAG, "Deleted previous json backup.")
            }
        }
        uploadFile(file, "application/json", config.folderId) {
            config.jsonFileId = it.id
            config.save()
            onDone()
        }
    }

    private fun syncImages() {
        val local = Node.fromFile(app.basedir)
        listFiles({  remoteFiles ->
            val root = local.merge(remoteFiles)
            root.diff(this)
        })
    }

    fun sync(onDone: () -> Unit) {
        createRoot {
            app.applicationScope.launch(Dispatchers.IO) {
                syncJson() {
                    syncImages()
                }
                app.flushOkHttp()
                config.save()
                onDone()
            }
        }

/*
 okCreateFolder(GoogleFile(
            id = "",
            name = "TestCreateFolder",
            mimeType = MimeType.FOLDER,
        )) {
            Log.d(TAG, "Folder created: $it")
        }
        createFolder(GoogleFile(
            id = "",
            name = "TestCreateFolder",
            mimeType = MimeType.FOLDER,
            folderId = config.folderId,
        )) {
            Log.d(TAG, "Folder created: $it")
        }
        val file = File(app.applicationContext?.filesDir, "lookup_stats.json")
        okUploadFile(file, "text/plain", config.folderId) {
            Log.d(TAG, "Uploaded: $it")
        }
        okListFiles( { files ->
            Log.d(TAG, "${files.size} files")
            files.forEach {
                Log.d(TAG, "-- $it")
            }
        })




        Log.d(TAG, "sync: started.")
        app.applicationScope.launch(Dispatchers.IO) {
            ensureFolder()
            val local = Node.fromFile(app.basedir)
            //syncJson()
            //config.save()
            val root = local.merge(listFiles())
            root.diff(this@SyncDrive)
            config.save()
            Log.d(TAG, "sync: finished")
            onDone()
        }
         */
    }
}
