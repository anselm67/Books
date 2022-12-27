package com.anselm.books

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ImportExport(private val repository: BookRepository,
                   private val contentResolver: ContentResolver,
                   private val basedir: File) {

    private suspend fun importJsonText(text: String): Int {
        // Parses the json stream into books.
        val tok  = JSONTokener(text)
        val root = tok.nextValue()
        if (root !is JSONObject) {
            Log.d(TAG, "Expected a top level object, " +
                    "got a ${root::class.qualifiedName} instead.")
            return -1
        }
        val books = root.get("books")
        if ( books !is JSONArray) {
            Log.d(TAG, "Expected 'books' as a list, " +
                    "got a ${books::class.qualifiedName} instead.")
            return -1
        }
        var count = 0
        repository.deleteAll()
        (0 until books.length()).forEach { i ->
            val obj = books.getJSONObject(i)
            try {
                val book = Book(obj)
                repository.save(book)
                count++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse $obj, skipping.")
            }
        }
        repository.invalidate()
        return count
    }

    private fun copy(inp: ZipInputStream, out: OutputStream?): Boolean {
        if (out == null)
            return false
        // Copy in the file as expected, converting exceptions to boolean return
        try {
            val buffer = ByteArray(8192)
            while (true) {
                val size = inp.read(buffer)
                if (size > 0) {
                    out.write(buffer)
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy zip stream into local file.", e)
            return false
        }
        return true
    }

    private fun readText(inp: ZipInputStream): String {
        return inp.bufferedReader().readText()
    }

    private suspend fun importZipInputStream(zipInputStream: ZipInputStream): Pair<Int, Int> {
        var entry: ZipEntry? = zipInputStream.nextEntry
        var bookCount = -1
        var imageCount = 0
        while (entry != null) {
            val file = File(basedir, entry.name)
            if (entry.name == "images"  && entry.isDirectory) {
                file.mkdirs()
            } else if (entry.name == "books.json") {
                bookCount = importJsonText(readText(zipInputStream))
            } else if (entry.name.startsWith("images")) {
                file.parentFile?.mkdirs()
                withContext(Dispatchers.IO) {
                    FileOutputStream(file).use { out ->
                        if (copy(zipInputStream, out)) {
                            imageCount++
                        } else {
                            Log.d(TAG, "Failed to extract ${entry!!.name}")
                        }
                    }
                }
            } else {
                Log.d(TAG, "Unexpected entry ${entry.name}, ignored.")
            }
            entry = zipInputStream.nextEntry
        }
        return Pair(bookCount, imageCount)
    }

    suspend fun importZipFile(uri: Uri): Pair<Int, Int> {
        var ret: Pair<Int, Int> = Pair(-1, 0)
        Log.d(TAG, "Importing $uri.")
        contentResolver.openInputStream(uri)?.use { input ->
            input.buffered(128 * 1024).use { zipInputStream ->
                ZipInputStream(zipInputStream).use {
                    ret = importZipInputStream(it)
                }
            }
        }
        Log.d(TAG,"Imported ${ret.first} books and ${ret.second} images from zip file $uri")
        return ret
    }
}