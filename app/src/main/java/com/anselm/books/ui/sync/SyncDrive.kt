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
import com.anselm.books.ui.sync.SyncDrive.MimeType.FOLDER
import com.google.api.client.googleapis.batch.BatchCallback
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpRequest
import com.google.api.services.drive.Drive
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


private data class GoogleFile(
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

    fun diff(drive: SyncDrive, parentFolderId: String? = null) {
        val batch = drive.Batch()
        if (folderId == null) {
            require(parentFolderId != null) { "parentFolderId required to createFolder." }
            batch.add(
                drive.createFolderRequest(localName, parentFolderId).buildHttpRequest()
            ) {
                folderId = it.id
            }
        }
        remoteFiles.forEach {
            if ( ! localFiles.contains(it.name)) {
                Log.d(TAG, "FETCH $localName/$it")
            }
        }
        localFiles.forEach { name ->
            if ( remoteFiles.firstOrNull{ it.name == name } == null ) {
                batch.add(
                    drive.uploadFileRequest(File(localDirectory, name), "image/heic", folderId).buildHttpRequest(),
                ) {
                    Log.d(TAG, "$localName/$name uploaded.")
                }
            }
        }
        batch.run()
        localChildren.forEach { it.diff(drive, folderId) }
    }

    private fun collectChildren(list: List<GoogleFile>) {
        list.forEach {
            if (it.folderId == folderId) {
                if (it.mimeType == FOLDER ) {
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
    private val drive: Drive,
    private val authToken: String,
) {
    private val config = SyncConfig.get()

    object MimeType {
        const val FOLDER = "application/vnd.google-apps.folder"
    }

    fun createFolderRequest(name: String, parentFolderId: String? = null): Drive.Files.Create {
        val folderData = com.google.api.services.drive.model.File()
        folderData.name = name
        if (parentFolderId != null) {
            folderData.parents = listOf(parentFolderId)
        }
        folderData.mimeType = FOLDER
        return drive.files().create(folderData)
    }

    private fun createFolder(name: String, parentFolderId: String? = null): String {
        val result = createFolderRequest(name, parentFolderId).execute()
        if (result == null) {
            throw SyncException("createFolder($name, $parentFolderId): empty response.")
        } else {
            return result.id
        }
    }

    private fun okCreateFolder(
        folder: GoogleFile,
        onResponse: (GoogleFile) -> Unit,
    ) {
        check(folder.mimeType == MimeType.FOLDER)
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrlOrNull()!!.newBuilder()
        val metadata = folder.toJson().toString()
        val req = Request.Builder()
            .url(url.build())
            .header("Authorization", "Bearer $authToken")
            .post(metadata.toRequestBody("application/json".toMediaType()))
            .build()
        run(req) { onResponse(GoogleFile.fromJson(it)) }
    }

    fun uploadFileRequest(file: File, type: String, folderId: String? = null): Drive.Files.Create {
        val googleFile = com.google.api.services.drive.model.File()
        googleFile.name = file.name
        folderId?.let {
            googleFile.parents = listOf(folderId)
        }
        val fileContent = FileContent(type, file)
        return drive.Files().create(googleFile, fileContent)
    }

    private fun uploadFile(file: File, type: String, folderId: String? = null): String {
        val result = uploadFileRequest(file, type, folderId).execute()
        if (result == null) {
            throw SyncException("uploadFile(${file.path}, $type, $folderId): empty response.")
        } else {
            return result.id
        }
    }

    private fun bodyToString(request: Request): String? {
        return try {
            val copy = request.newBuilder().build()
            val buffer = okio.Buffer()
            copy.body!!.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: IOException) {
            "did not work"
        }
    }

    private fun okUploadFile(
        file: File,
        mimeType: String,
        folderId: String? = null,
        onResponse: (GoogleFile) -> Unit,
    ): String? {
        val url = "https://www.googleapis.com/upload/drive/v3/files".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("uploadType", "multipart")
        val metadata = GoogleFile("", file.name, mimeType, folderId).toJson().toString()
        val multipartBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody("application/json".toMediaType()))
            .addPart(file.asRequestBody(mimeType.toMediaType()))
            .build()
        val req = Request.Builder()
            .url(url.build())
            .header("Authorization", "Bearer $authToken")
            .header("Content-Length", multipartBody.contentLength().toString())
            .post(multipartBody)
            .build()

        Log.d(TAG, "Here comes the request ...")
        bodyToString(req)?.let { Log.d(TAG, it) }
        run(req) {
            onResponse(GoogleFile.fromJson(it))
        }
        return null
    }

    private fun listFiles(): List<com.google.api.services.drive.model.File> {
        var token: String? = null
        val files = mutableListOf<com.google.api.services.drive.model.File>()
        try {
            do {
                val request = drive.files().list()
                    .setQ("trashed = false")
                    .setFields("nextPageToken, files(id, name, mimeType, parents)")
                    .setSpaces("drive")
                if (token != null) {
                    request.pageToken = token
                }
                val result = request.execute()
                files.addAll(result.files)
                token = result.nextPageToken
            } while (token != null)
            return files
        } catch (e: Exception) {
            throw SyncException("listFiles(): failed.", e)
        }
    }

    private fun run(req: Request, onResponse: (JSONObject) -> Unit) {
        val url = req.url
        app.okHttp.newCall(req).enqueue(object: Callback{
            override fun onFailure(call: Call, e: IOException) {
                // FIXME
                Log.e(TAG, "${call.request().url}: request failed.", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (it.isSuccessful) {
                            val tok = JSONTokener(response.body!!.string())
                            val obj = tok.nextValue()
                            if (obj !is JSONObject) {
                                Log.e(TAG, "$url: parse failed got a ${obj.javaClass.name}.")
                            } else {
                                onResponse(obj)
                            }
                        } else {
                            // FIXME
                            Log.d(TAG, "$url: status ${response.code}.")
                        }
                    } catch (e: Exception) {
                        // FIXME
                        Log.e(TAG, "$url: handling failed.", e)
                    }
                }
            }

        })
    }

    // {
    // "kind": "drive#fileList",
    // "nextPageToken": "~!!~AI9FV7RDd1-hxSvk9KffIpsc7Tw0yzrL9jRRLr7G2awJMZPyeq1gheB9UA9z6h_Ph07XeMhAAenJzzHrId3p7NNep4IkdYW4k3T2DgGy3ulmTbi3jWvsrHx2TIjdEvB4Ii1-tzCxsMkPv4_337aiUit7H-YpiQGc4l0Vr2iNcJ-CEAysKQ6xehTGuIDVMo0pHWLpCprQEWvy_NbceZ6sUfLXe4SigUN5FVGHF8JU8Uifj9uqo6iFHtC5roAx1qS9YJHABp5EGuSSEZqvm1eLa6c3o5aU8bo3LdbSfB58VsqaeFRHJRalXIaqFEBq7jKHN7HNtszAuHxT",
    // "incompleteSearch": false,
    // "files": [
    //  {
    //   "kind": "drive#file",
    //   "id": "19I_QaSGmjqePN66PN5yqKq0WhnV_dSbs",
    //   "name": "images",
    //   "mimeType": "application/vnd.google-apps.folder"
    //  },
    //}
    private fun okListFiles(
        onResponse: (List<GoogleFile>) -> Unit,
        pageToken: String? = null,
        into: MutableList<GoogleFile>? = null)
    {
        val files = into ?: mutableListOf<GoogleFile>()
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("q", "trashed = false")
            .addQueryParameter("fields", "nextPageToken, files(id, name, mimeType, parents)")
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("pageSize", "500")
        if (pageToken != null) {
            url.addQueryParameter("pageToken", pageToken)
        }
        run(Request.Builder()
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
                okListFiles(onResponse, nextToken, files)
            }
        }
    }

    private fun deleteRequest(fileId: String): Drive.Files.Delete? {
        return drive.Files().delete(fileId)
    }

    fun delete(fileId: String) {
        try {
            drive.Files().delete(fileId).execute()
        } catch (e: Exception) {
            throw SyncException("delete($fileId) failed.", e)
        }
    }

    private fun ensureFolder(): String {
        if (config.folderId.isEmpty()) {
            config.folderId = createFolder(Constants.DRIVE_FOLDER_NAME)
            config.save()
        }
        return config.folderId
    }

    private suspend fun syncJson() {
        val file = File(app.applicationContext.cacheDir, "books.json")
        file.deleteOnExit()
        file.outputStream().use {
            app.importExport.exportJson(it)
        }
        if (config.jsonFileId.isNotEmpty()) {
            delete(config.jsonFileId)
        }
        config.jsonFileId = uploadFile(file, "application/json", config.folderId)
    }

    inner class Batch(
        private val batch: BatchRequest = drive.batch()
    ) {
        fun add(request: HttpRequest, onFile: (com.google.api.services.drive.model.File) -> Unit) {
            batch.queue(
                request,
                com.google.api.services.drive.model.File::class.java,
                GoogleJsonError::class.java,
                object: BatchCallback<com.google.api.services.drive.model.File, GoogleJsonError> {
                    override fun onSuccess(googleFile: com.google.api.services.drive.model.File?, responseHeaders: HttpHeaders?) {
                        if (googleFile != null) {
                            onFile(googleFile)
                        } else {
                            // FIXME Error
                        }
                    }
                    override fun onFailure(e: GoogleJsonError?, responseHeaders: HttpHeaders?) {
                        TODO("Not yet implemented")
                    }
                }
            )
        }

        fun run() {
            if (batch.size() > 0) {
                batch.execute()
            }
        }
    }

    fun xxsync() {
        val folderData = com.google.api.services.drive.model.File()
        folderData.name = "BatchTest"
        folderData.mimeType = FOLDER
        val request = drive.files().create(folderData)

        drive.batch().queue(
            request.buildHttpRequest(),
            GoogleFile::class.java,
            GoogleJsonError::class.java,
            object: BatchCallback<GoogleFile, GoogleJsonError> {
                override fun onSuccess(t: GoogleFile?, responseHeaders: HttpHeaders?) {
                    Log.d(TAG, "onSuccess: got $t")
                }

                override fun onFailure(e: GoogleJsonError?, responseHeaders: HttpHeaders?) {
                    Log.e(TAG, "onFailure: $e")
                }
            }
        ).execute()

    }

    fun sync(onDone: () -> Unit) {
        okCreateFolder(GoogleFile(
            id = "",
            name = "TestCreateFolder",
            mimeType = MimeType.FOLDER,
            folderId = config.folderId,
        )) {
            Log.d(TAG, "Folder created: $it")
        }
        /*
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
