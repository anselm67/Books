package com.anselm.books.lookup

import android.util.Log
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import org.json.JSONObject
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.*


// https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/LookupExamples.html
class iTuneClient: JsonClient() {
    private val dateFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    private val markUpRE = Regex("<[^>]*>")
    private fun removeMarkUp(src: String): String {
        return markUpRE.replace(src, "")
    }

    private fun convert(
        resp: JSONObject,
        onBook: (matches: Book?) -> Unit,
    ) {
        val itemCount = resp.getInt("resultCount")
        if (itemCount <= 0) {
            onBook(null)
            return
        }
        // Converts the match and let the callback know about the outcome.
        val item = resp.getJSONArray("results").get(0) as JSONObject
        val book = Book()
        book.title = item.optString("trackName", "")
        val artistName = item.optString("artistName", "")
        if (artistName.isNotEmpty()) {
            book.authors = listOf(app.repository.labelB(Label.Type.Authors, artistName))
        }
        val genreNames = item.optJSONArray("genres")
        if (genreNames != null && genreNames.length() > 0) {
            book.genres = (0 until genreNames.length()).map {
                app.repository.labelB(Label.Type.Genres, genreNames.getString(it))
            }
        }
        val artWorkUrl = item.optString("artworkUrl100", "")
        if (artWorkUrl.isNotEmpty()) {
            book.imgUrl = artWorkUrl
        }
        val releaseDate = item.optString("releaseDate", "")
        if (releaseDate.isNotEmpty()) {
            try {
                val date = dateFormatter.parse(releaseDate)
                book.yearPublished = date.get(ChronoField.YEAR).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse date '$releaseDate' (ignored).", e)
            }
        }
        val description = item.optString("description", "")
        if (description.isNotEmpty()) {
            book.summary = removeMarkUp(description)
        }
        onBook(book)
    }

    override fun lookup(
        tag: String,
        isbn: String,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (matches: Book?) -> Unit
    ) {
        val url = "https://itunes.apple.com/lookup?isbn=$isbn"
        runRequest(tag, url, onError,onBook) {
            convert(it, onBook)
        }
    }
}