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
import com.anselm.books.ui.sync.SyncDrive.MimeType.FOLDER
import com.google.api.client.googleapis.batch.BatchCallback
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpRequest
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

typealias GoogleFile = com.google.api.services.drive.model.File

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
            if (it.parents.contains(folderId)) {
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

    private fun listFiles(): List<GoogleFile> {
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
        fun add(request: HttpRequest, onFile: (GoogleFile) -> Unit) {
            batch.queue(
                request,
                GoogleFile::class.java,
                GoogleJsonError::class.java,
                object: BatchCallback<GoogleFile, GoogleJsonError> {
                    override fun onSuccess(googleFile: GoogleFile?, responseHeaders: HttpHeaders?) {
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
    }
}
