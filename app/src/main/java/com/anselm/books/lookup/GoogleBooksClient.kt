package com.anselm.books.lookup

import android.util.Log
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import org.json.JSONObject


class GoogleBooksClient: JsonClient() {
    val repository by lazy { app.repository }

    private fun extractIsbn(obj: JSONObject): String {
        val ids = obj.optJSONArray("industryIdentifiers")
        return if (ids == null) {
            ""
        } else {
            arrayToList<JSONObject>(ids).firstOrNull() {
                it.optString("type", "") == "ISBN_13"
            }?.optString("identifier", "") ?: ""
        }
    }

    private val languages = mapOf<String, String>(
        "fr" to "French",
        "en" to "English",
        "es" to "Spanish",
    )

    private fun getLanguage(str: String): Label? {
        return app.repository.labelOrNullB(Label.Type.Language, languages.getOrDefault(str, str))
    }

    private fun getNumberOfPages(num: Int): String {
        return if (num == 0) "" else num.toString()
    }

    private fun convert(obj: JSONObject): Book {
        val book = app.repository.newBook()
        val volumeInfo = obj.optJSONObject("volumeInfo")
        check(volumeInfo != null)
        book.isbn = extractIsbn(volumeInfo)
        book.title = volumeInfo.optString("title", "")
        book.subtitle = volumeInfo.optString("subtitle", "")
        // FIXME book.yearPublished
        book.summary = volumeInfo.optString("description")
        book.imgUrl = volumeInfo.optJSONObject("imageLinks")?.optString("thumbnail") ?: ""
        book.language = getLanguage(volumeInfo.optString("language", ""))
        book.numberOfPages = getNumberOfPages(volumeInfo.optInt("pageCount", 0))
        book.publisher = app.repository.labelOrNullB(
            Label.Type.Publisher, volumeInfo.optString("publisher", ""))
        // Label-fields:
        book.authors = arrayToList<String>(volumeInfo.optJSONArray("authors"))
            .map { repository.labelB(Label.Type.Authors, it) }
        if ( app.prefs.getBoolean("lookup_use_only_existing_genres", false)) {
            book.genres = arrayToList<String>(volumeInfo.optJSONArray("categories"))
                .mapNotNull { repository.labelIfExistsB(Label.Type.Genres, it) }
        } else {
            book.genres = arrayToList<String>(volumeInfo.optJSONArray("categories"))
                .map { repository.labelB(Label.Type.Genres, it) }
        }
        return book
    }

    private fun convert(
        resp: JSONObject,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit
    ) {
        // Checks the kind, verifies we got some matches.
        check(resp.optString("kind") == "books#volumes")
        val itemCount = resp.getInt("totalItems")
        if (itemCount <= 0) {
            onBook(null)
            return
        }
        // Converts the match and let the callback know about the outcome.
        val items = resp.getJSONArray("items")
        for (i in 0 until itemCount) {
            try {
                onBook(convert(items.getJSONObject(i)))
                // FIXME For now first match only.
                break
            } catch (e: Exception) {
                val msg =  "Error while parsing item at position $i."
                Log.e(TAG, msg, e)
                onError(msg, e)
            }
        }
    }

    override fun lookup(
        tag: String,
        isbn: String,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit,
    ) {
        val url = "https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn"
        runRequest(tag, url, onError, onBook) {
            convert(it, onError, onBook)
        }
    }
}