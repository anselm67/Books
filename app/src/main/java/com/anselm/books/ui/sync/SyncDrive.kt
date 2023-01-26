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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File


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

    private fun diffChildren(job: SyncJob) {
        check(folderId != null)
        remoteFiles.forEach {
            if ( ! localFiles.contains(it.name)) {
                Log.d(TAG, "FETCH $localName/$it")
            }
        }
        localFiles.forEach { name ->
            if ( remoteFiles.firstOrNull{ it.name == name } == null ) {
                job.uploadFile(File(localDirectory, name), "image/heic", folderId) {
                    Log.d(TAG, "$localName/$name uploaded.")
                }
            }
        }
        localChildren.forEach { it.diff(job, folderId) }
    }

    fun diff(
        job: SyncJob,
        parentFolderId: String? = null,
    ) {
        if (folderId == null) {
            require(parentFolderId != null) { "parentFolderId required to createFolder." }
            job.createFolder(localName, parentFolderId) {
                folderId = it.id
                diffChildren(job)
            }
        } else {
            diffChildren(job)
        }
    }

    private fun collectChildren(list: List<GoogleFile>) {
        list.forEach {
            if (it.folderId == folderId) {
                if (it.mimeType == MimeType.APPLICATION_FOLDER) {
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

    @Suppress("unused")
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

    private fun createRoot(job: SyncJob, onDone: () -> Unit) {
        if (config.folderId.isEmpty()) {
            job.createFolder(Constants.DRIVE_FOLDER_NAME) {
                config.folderId = it.id
                onDone()
            }
        } else {
            onDone()
        }
    }

    private suspend fun syncJson(job: SyncJob, onDone: () -> Unit) {
        val file = File(app.applicationContext.cacheDir, "books.json")
        file.deleteOnExit()
        file.outputStream().use {
            app.importExport.exportJson(it)
        }
        if (config.jsonFileId.isNotEmpty()) {
            job.delete(config.jsonFileId) {
                Log.d(TAG, "Deleted previous json backup.")
            }
        }
        job.uploadFile(file, "application/json", config.folderId) {
            config.jsonFileId = it.id
            config.save()
            onDone()
        }
    }

    private fun syncImages(job: SyncJob) {
        val local = Node.fromFile(app.basedir)
        job.listFiles({  remoteFiles ->
            val root = local.merge(remoteFiles)
            root.diff(job)
        })
        // We're no longer explicitly adding requests to this job.
        job.done()
    }

    fun sync(onDone: (SyncJob) -> Unit): SyncJob {
        val job = SyncJob(authToken)
        job.start {
            createRoot(job) {
                app.applicationScope.launch(Dispatchers.IO) {
                    syncJson(job) {
                        syncImages(job)
                    }
                    config.save()
                }
            }
            job.flush()
            onDone(job)
        }
        return job
    }
}
