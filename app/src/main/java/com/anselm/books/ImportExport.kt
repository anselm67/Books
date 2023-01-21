package com.anselm.books

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.database.Book
import com.anselm.books.database.BookRepository
import com.anselm.books.database.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
        (0 until books.length()).forEach { i ->
            val obj = books.getJSONObject(i)
            try {
                /*
                 * Note how this doesn't go through the repository, and therefore doesn't go
                 * through its listeners - such as lastLocation - which is intended.
                 */
                val book = Book(obj)
                /**
                 * If your zip file doesn't follow the imageFilename convention described in
                 * ImageRepository.save() you can call
                 * app.imageRepository.fix(book)
                 * here, which will rename the image file properly and update the books's
                 * imageFilename accordingly.
                 */
                repository.save(book)
                count++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse $obj, skipping.", e)
            }
        }
        // FIXME repository.invalidate()
        return count
    }

    private fun copy(inp: ZipInputStream, out: OutputStream?): Boolean {
        if (out == null)
            return false
        // Copy in the file as expected, converting exceptions to boolean return
        try {
            inp.copyTo(out)
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
            if (entry.name == app.imageRepository.imageDirectoryName  && entry.isDirectory) {
                file.mkdirs()
            } else if (entry.name == "books.json") {
                bookCount = importJsonText(readText(zipInputStream))
            } else if (entry.name.startsWith(app.imageRepository.imageDirectoryName)) {
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
        repository.deleteAll()
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

    private suspend fun exportJson(out: OutputStream): Int {
        // Convert all books to JSON, hold them tight(!)
        val jsonBooks = JSONArray()
        var offset = 0
        val limit = 250
        do {
            val books = repository.getPagedList(Query.emptyQuery, limit, offset)
            books.map {
                repository.decorate(it)
                jsonBooks.put(it.toJson())
            }
            offset += books.size
        } while (books.isNotEmpty())
        val jsonRoot = JSONObject()
        jsonRoot.put("books", jsonBooks)
        // Write it all to the given URI.
        val text = jsonRoot.toString(2)
        val writer = OutputStreamWriter(out, Charsets.UTF_8)
        withContext(Dispatchers.IO) {
            writer.write(text)
            writer.flush()
        }
        return offset
    }

    private suspend fun exportZipFile(zipFile: File, dest:Uri): Int {
        // Collects all the files that need to be included.
        val zipOut = ZipOutputStream(zipFile.outputStream())
        var bookCount: Int
        zipOut.use {
            // Writes out the json file.
            it.putNextEntry(ZipEntry("books.json"))
            bookCount = exportJson(it)
            // And all the images.
            app.imageRepository.imageDirectory.walk().forEach { file ->
                val path = file.relativeTo(basedir).path
                if (file.isFile) {
                    it.putNextEntry(ZipEntry(path))
                    FileInputStream(file).use { inputStream -> inputStream.copyTo(it) }
                }
            }
        }
        // Copies the temp file into the provided destination.
        contentResolver.openFileDescriptor(dest, "w").use { fd ->
            FileOutputStream(fd?.fileDescriptor).use {
                FileInputStream(zipFile).copyTo(it)
            }
        }
        return bookCount
    }

    suspend fun exportZipFile(dest: Uri): Int {
        val zipFile = File(app.cacheDir, "books.zip")
        try {
            return exportZipFile(zipFile, dest)
        } finally {
            zipFile.delete()
        }
    }
}