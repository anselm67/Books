package com.anselm.books.openlibrary

import android.util.Log
import com.anselm.books.BooksApplication
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject


class GoogleBooksClient: SimpleClient() {
    val repository by lazy { BooksApplication.app.repository }

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

    private fun getLanguage(str: String): String {
        return languages.getOrDefault(str, str)
    }

    private fun getNumberOfPages(num: Int): String {
        return if (num == 0) "" else num.toString()
    }

    private inline fun <reified T: Any> arrayToList(a: JSONArray?): List<T> {
        return if (a == null) {
            emptyList()
        } else {
            (0 until a.length()).map { (a.get(it) as T) }
        }
    }

    private fun convert(obj: JSONObject): Book {
        val book = Book()
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
        book.publisher = volumeInfo.optString("publisher", "")
        // Label-fields:
        runBlocking {
            book.authors = arrayToList<String>(volumeInfo.optJSONArray("authors"))
                .map { repository.label(Label.Type.Authors, it) }
            book.genres = arrayToList<String>(volumeInfo.optJSONArray("categories"))
                .map { repository.label(Label.Type.Genres, it) }
        }
        return book
    }

    private fun convert(
        resp: JSONObject,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (Book) -> Unit
    ) {
        check(resp.optString("kind") == "books#volumes")
        val itemCount = resp.getInt("totalItems")
        val items = resp.getJSONArray("items")

        for (i in 0 until itemCount) {
            try {
                onBook(convert(items.getJSONObject(i)))
                // FIXME For now first match only.
                break
            } catch (e: Exception) {
                val msg =  "Error while parsing item $i."
                Log.e(TAG, msg, e)
                onError(msg, e)
            }
        }
    }

    override fun lookup(
        isbn: String,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (Book) -> Unit,
    ) {
        val url = "https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn"
        runRequest(url, onError) {
            convert(it, onError, onBook)
        }

    }
}