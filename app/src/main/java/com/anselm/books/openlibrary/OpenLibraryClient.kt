package com.anselm.books.openlibrary

import android.util.Log
import com.anselm.books.Book
import com.anselm.books.TAG
import okhttp3.*
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAccessor

class OpenLibraryClient {
    private val client = OkHttpClient()
    private val basedir = "https://openlibrary.org"

    private fun runRequest(
        url: String,
        onError: (message: String, e: Exception?) -> Unit,
        onSuccess: (JSONObject) -> Unit
    ): Call {
        val req = Request.Builder().url(url).build()
        val call = client.newCall(req)

        call.enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("$url: get failed.", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if ( response.isSuccessful ) {
                    val tok = JSONTokener(response.body!!.string())
                    val obj = tok.nextValue()
                    if (obj !is JSONObject) {
                        onError("$url: parse failed got a ${obj.javaClass.name}.", null)
                        return
                    }
                    onSuccess(obj)
                } else {
                    onError( "$url: HTTP Request failed, status $response", null)
                }
            }
        })
        return call
    }

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
        val vals = obj.optJSONArray(key) ?: return null
        if (vals.length() > 0) {
            val keyval = vals.get(0)
            if (keyval is JSONObject) {
                return keyval.optString("key")
            }
        }
        return null
    }
    private fun language(obj: JSONObject): String {
        val tag = firstKeyOrNull(obj, "languages") ?: return ""
        return languages[tag] ?: ""
    }

    private fun foldAll(obj: JSONObject, key: String): String {
        val list = obj.optJSONArray(key) ?: return ""
        if (list.length() > 0) {
            val arr = ArrayList<String>()
            for (i: Int in 0 until list.length())
                arr.add(list[i] as String)
            return arr.joinToString()
        }
        return ""
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
                calls[i] = (runRequest("https://openlibrary.org${keys[i]}.json",
                    {  msg, e ->        // onError fails immediately, ignore further responses.
                        for (call in calls) {
                            call?.cancel()
                        }
                        onError(msg, e)
                    },
                    {
                        values[i] = it.optString("name")
                        if (++ done == keys.size) {
                            book.author = values.joinToString()
                            onBook(book)
                        }
                    }))
            }
        } else {
            onBook(book)
        }
    }

    private fun setupWorkAndAuthors(
        book: Book,
        work:JSONObject,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit
    ) {
        book.summary = extractValue(work.optJSONObject("description"), "value")
        book.genre = foldAll(work, "subjects")
        if (book.subtitle == "") {
            book.subtitle = work.optString("subtitle", "")
        }
        if (book.imgUrl == "") {
            book.imgUrl = coverUrl(work)
        }
        doAuthors(book, work, onError, onBook)
    }

    private fun doWorkAndAuthors(
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
            runRequest(url, onError) {
                setupWorkAndAuthors(book, it, onError, onBook)
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

    private fun setup(
        book: Book,
        obj: JSONObject,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit) {
        // Copies all the pass through fields.
        book.title = obj.optString("title","")
        book.subtitle = obj.optString("subtitle", "")
        book.numberOfPages = obj.optString("number_of_pages", "")
        book.isbn = firstOrEmpty(obj, "isbn_13")
        book.language = language(obj)
        book.publisher = foldAll(obj, "publishers")
        book.imgUrl = coverUrl(obj)
        val date = publishDate(obj)
        if (date != null) {
            // TODO We could get additional fields and use all the available precision of date.
            book.yearPublished = date.get(ChronoField.YEAR).toString()
        }
        // Continues our journey to fetch additional infos about hte work, when available:
        doWorkAndAuthors(book, obj, onError, onBook)
    }

    fun lookup(
        isbn: String,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit
    ) {
        val url = "$basedir/isbn/$isbn.json"
        runRequest(url, onError) {
            setup(Book(),it, onError, onBook)
        }
    }
}
