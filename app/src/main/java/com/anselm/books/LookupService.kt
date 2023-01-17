package com.anselm.books

import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.database.Book
import com.anselm.books.lookup.AmazonImageClient
import com.anselm.books.lookup.GoogleBooksClient
import com.anselm.books.lookup.OclcClient
import com.anselm.books.lookup.OpenLibraryClient
import com.anselm.books.lookup.SimpleClient
import com.anselm.books.lookup.iTuneClient
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KProperty0

private data class LookupServiceClient(
    val preferenceGetter: KProperty0.Getter<Boolean>,
    val client: SimpleClient,
)

class LookupService {
    private val prefs by lazy {
        app.bookPrefs
    }

    private val clients = listOf(
        LookupServiceClient(prefs::useGoogle.getter, GoogleBooksClient()),
        LookupServiceClient(prefs::useWorldcat.getter, OclcClient()),
        LookupServiceClient(prefs::useiTunes.getter, iTuneClient()),
        LookupServiceClient(prefs::useAmazon.getter, AmazonImageClient()),
        LookupServiceClient(prefs::useOpenLibrary.getter, OpenLibraryClient()),
    )

    private val requestIdCounter = AtomicInteger(1)
    private fun nextTag(): String {
        return "lookup-${requestIdCounter.incrementAndGet()}"
    }

    private fun onCompletion(index: Int, tag: String, book: Book, onDone: (Book?) -> Unit) {
        for (i in index until clients.size) {
            val service = clients[i]
            if (service.preferenceGetter()) {
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