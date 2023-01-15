package com.anselm.books.lookup

import android.util.Log
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.database.Book
import com.anselm.books.TAG
import com.anselm.books.database.Label
import okhttp3.*
import org.json.JSONObject
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAccessor

class OpenLibraryClient: JsonClient() {
    private val basedir = "https://openlibrary.org"

    private fun firstOrEmpty(obj: JSONObject, key: String): String {
        val value = obj.optJSONArray(key) ?: return ""
        return value.optString(0, "")
    }

    private val languages = mapOf (
        "/languages/fre" to "French",
        "/languages/eng" to "English",
        "/languages/spa" to "Spanish",
    )

    private fun firstKeyOrNull(obj: JSONObject, key: String): String? {
        val values = obj.optJSONArray(key) ?: return null
        if (values.length() > 0) {
            val keyValue = values.get(0)
            if (keyValue is JSONObject) {
                return keyValue.optString("key")
            }
        }
        return null
    }

    private fun language(obj: JSONObject): String {
        val tag = firstKeyOrNull(obj, "languages") ?: return ""
        return languages[tag] ?: ""
    }

    private fun asStringArray(obj: JSONObject, key: String): List<String> {
        val list = obj.optJSONArray(key) ?: return emptyList()
        return if (list.length() > 0) {
            val arr = ArrayList<String>()
            for (i: Int in 0 until list.length())
                arr.add(list[i] as String)
            return arr
        } else {
            emptyList()
        }
    }

    private fun coverUrl(obj: JSONObject): String {
        val coverId = firstOrEmpty(obj,"covers")
        if (coverId != "") {
            return "https://covers.openlibrary.org/b/id/${coverId}-L.jpg"
        }
        return ""
    }

    private fun publishDate(obj: JSONObject): TemporalAccessor? {
        val value = obj.optString("publish_date") ?: return null
        for (fmt in dateFormatters) {
            try {
                return fmt.parse(value)
            } catch (e: DateTimeParseException) {
                // Ignored.
            }
        }
        Log.d(TAG, "Failed to parse date: $value.")
        return null
    }

    // typedObject -> { "type": "/type/<some-type>", "valueKey": "the value" }
    private fun extractValue(typedObject: JSONObject?, valueKey: String): String {
        val value = typedObject?.optString(valueKey, "")
        return value ?: ""
    }

    // authors -> {JSONArray@26790} "[{"type":{"key":"\/type\/author_role"},"author":{"key":"\/authors\/OL35793A"}},{"type":{"key":"\/type\/author_role"},"author":{"key":"\/authors\/OL892424A"}}]"
    private fun extractAuthorsFromWork(work: JSONObject): List<String>? {
        val list = work.optJSONArray("authors") ?: return null
        if (list.length() == 0) {
            return null
        }
        val keys = mutableListOf<String>()
        for (i in 0 until list.length()) {
            val value = list.get(i)
            if (value is JSONObject) {
                val key = value.optJSONObject("author")?.optString("key")
                if (key != null) {
                    keys.add(key)
                }
                Log.d(TAG, "Author key $key")
            }
        }
        return keys
    }

    private fun doAuthors(
        tag: String,
        book: Book,
        work:JSONObject,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit
    ) {
        val keys = extractAuthorsFromWork(work)
        if (keys != null && keys.isNotEmpty()) {
            val calls = arrayOfNulls<Call>(keys.size)
            val values = arrayOfNulls<String>(keys.size)
            var done = 0
            for (i in keys.indices) {
                calls[i] = (runRequest(tag, "https://openlibrary.org${keys[i]}.json",
                    {  msg, e ->        // onError fails immediately, ignore further responses.
                        for (call in calls) {
                            call?.cancel()
                        }
                        onError(msg, e)
                    },
                    onBook,
                    {
                        values[i] = it.optString("name")
                        if (++ done == keys.size) {
                            book.authors = values.filter { ! it.isNullOrEmpty() }
                                .map { app.repository.labelB(Label.Type.Authors, it!!) }
                            onBook(book)
                        }
                    }
                ))
            }
        } else {
            onBook(book)
        }
    }

    private fun getDescription(work: JSONObject): String {
        val value = work.opt("description")
        return if (value is String) {
            // We might have a direct string description.
            value
        } else {
            // Or an RDF-like typed value:
            extractValue(work.optJSONObject("description"), "value")
        }
    }

    private fun setupWorkAndAuthors(
        tag: String,
        book: Book,
        work:JSONObject,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit
    ) {
        book.summary = getDescription(work)
        if ( app.prefs.getBoolean("lookup_use_only_existing_genres", false)) {
            book.genres = arrayToList<String>(work.optJSONArray("subjects"))
                .mapNotNull { app.repository.labelIfExistsB(Label.Type.Genres, it) }
        } else {
            book.genres = arrayToList<String>(work.optJSONArray("subjects"))
                .map { app.repository.labelB(Label.Type.Genres, it) }
        }
        if (book.subtitle == "") {
            book.subtitle = work.optString("subtitle", "")
        }
        if (book.imgUrl == "") {
            book.imgUrl = coverUrl(work)
        }
        doAuthors(tag, book, work, onError, onBook)
    }

    private fun doWorkAndAuthors(
        tag: String,
        book: Book,
        obj:JSONObject,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit
    ) {
        val key = firstKeyOrNull(obj, "works")
        if (key == null) {
            onBook(book)
        } else {
            val url = "https://openlibrary.org$key.json"
            runRequest(tag, url, onError, onBook) {
                setupWorkAndAuthors(tag, book, it, onError, onBook)
            }
        }
    }

    private val dateFormatters = arrayOf(
        DateTimeFormatter.ofPattern("MMMM d, yyyy"),
        DateTimeFormatter.ofPattern("MMMM yyyy"),
        DateTimeFormatter.ofPattern("MMM d, yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM"),
        DateTimeFormatter.ofPattern("yyyy MMMM"),
        DateTimeFormatter.ofPattern("yyyy"),
    )

    private fun convert(
        tag: String,
        obj: JSONObject,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit) {
        val book = app.repository.newBook()
        // Copies all the pass through fields.
        book.title = obj.optString("title","")
        book.subtitle = obj.optString("subtitle", "")
        book.numberOfPages = obj.optString("number_of_pages", "")
        book.isbn = firstOrEmpty(obj, "isbn_13")
        book.language = app.repository.labelOrNullB(Label.Type.Language, language(obj))
        book.publisher = app.repository.labelOrNullB(
            Label.Type.Publisher, asStringArray(obj, "publishers").joinToString())
        book.imgUrl = coverUrl(obj)
        val date = publishDate(obj)
        if (date != null) {
            // TODO We could get additional fields and use all the available precision of date.
            book.yearPublished = date.get(ChronoField.YEAR).toString()
        }
        // Continues our journey to fetch additional infos about the work, when available:
        doWorkAndAuthors(tag, book, obj, onError, onBook)
    }

    override fun lookup(
        tag: String,
        isbn: String,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit
    ) {
        val url = "$basedir/isbn/$isbn.json"
        runRequest(tag, url, onError, onBook) {
            convert(tag, it, onError, onBook)
        }
    }
}
