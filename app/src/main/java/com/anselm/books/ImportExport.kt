package com.anselm.books

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class ImportExport(private val repository: BookRepository,
                   private val contentResolver: ContentResolver) {

    // Reads the entire content of the file.
    private fun readFile(uri: Uri): String? {
        var text: String? = null
        try {
            contentResolver.openInputStream(uri).use {
                text = it?.bufferedReader()?.readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while reading $uri.", e)
        }
        return text
    }

    /**
     * Parses the given [uri] content as a json repository of books.
     * @Returns The number of books imported.
     */
    suspend fun importJsonFile(uri: Uri): Int {
        val text: String? = readFile(uri)
        if (text == null) {
            Log.d(TAG, "Failed to read from {uri}, no books imported.")
            return -1
        }
        // Parses the json stream into books.
        val tok  = JSONTokener(text)
        val root = tok.nextValue()
        if (root !is JSONObject) {
            Log.d(TAG, "In file $uri expected a toplevel object, " +
                    "got a ${root::class.qualifiedName} instead.")
            return -1
        }
        val books = root.get("books")
        if ( books !is JSONArray) {
            Log.d(TAG, "In file $uri expected 'books' as a list, " +
                    "got a ${books::class.qualifiedName} instead.")
            return -1
        }
        var count = 0
        repository.deleteAll()
        (0 until books.length()).forEach { i ->
            val obj = books.getJSONObject(i)
            try {
                val book = Book(obj)
                repository.insert(book)
                count++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse $obj, skipping.")
            }
        }
        repository.invalidate()
        Log.d(TAG, "Created ${books.length()} books.")
        return count
    }
}