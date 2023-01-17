package com.anselm.books

import android.content.SharedPreferences
import android.util.Log
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.database.Book
import com.anselm.books.lookup.AmazonImageClient
import com.anselm.books.lookup.GoogleBooksClient
import com.anselm.books.lookup.OclcClient
import com.anselm.books.lookup.OpenLibraryClient
import com.anselm.books.lookup.SimpleClient
import com.anselm.books.lookup.iTuneClient
import java.util.concurrent.atomic.AtomicInteger

private data class LookupServiceClient (
    val preferenceName: String,
    val client: SimpleClient,
    var isEnabled: Boolean = true
)

class LookupService {
    private val clients = listOf(
        LookupServiceClient("use_google", GoogleBooksClient()),
        LookupServiceClient("use_worldcat", OclcClient()),
        LookupServiceClient("use_itunes", iTuneClient()),
        LookupServiceClient("use_amazon", AmazonImageClient()),
        LookupServiceClient("use_open_library", OpenLibraryClient()),
    )

    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            clients.forEach {
                if (it.preferenceName == key) {
                    it.isEnabled = prefs?.getBoolean("lookup_use_last_location", true) == true
                }
            }
        }

    init {
        app.prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        clients.forEach {
            it.isEnabled = app.prefs.getBoolean(it.preferenceName, true) == true
        }
    }

    private val requestIdCounter = AtomicInteger(1)
    private fun nextTag(): String {
        return "lookup-${requestIdCounter.incrementAndGet()}"
    }

    private fun onCompletion(index: Int, tag: String, book: Book, onDone: (Book?) -> Unit) {
        for (i in index until clients.size) {
            val service = clients[i]
            if (service.isEnabled) {
                Log.d(TAG, "Launching at ${index}: ${service.preferenceName}")
                service.client.lookup(tag, book) {
                    onCompletion(i + 1, tag, book, onDone)
                }
                return
            }
        }
        onDone(if (book.title.isNotEmpty()) book else null)
    }

    /**
     * Lookup the given ISBN through our enabled lookup services.
     * Lookup services errors are logged and simply considered no match, we only take in a
     * match callback.
     * Returns an okHttp that can be given to cancelHttpRequests to cancel all pending lookups
     * for the given ISBN.
     */
    fun lookup(isbn: String, onDone: (book: Book?) -> Unit): String {
        val tag = nextTag()
        val book = app.repository.newBook(isbn)
        onCompletion(0, tag, book, onDone)
        return tag
    }

}