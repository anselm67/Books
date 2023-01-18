package com.anselm.books

import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.database.Book
import com.anselm.books.lookup.AmazonImageClient
import com.anselm.books.lookup.BnfClient
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
        LookupServiceClient(prefs::useBNF.getter, BnfClient()),
        LookupServiceClient(prefs::useWorldcat.getter, OclcClient()),
        LookupServiceClient(prefs::useiTunes.getter, iTuneClient()),
        LookupServiceClient(prefs::useAmazon.getter, AmazonImageClient()),
        LookupServiceClient(prefs::useOpenLibrary.getter, OpenLibraryClient()),
    )

    private val requestIdCounter = AtomicInteger(1)
    private fun nextTag(): String {
        return "lookup-${requestIdCounter.incrementAndGet()}"
    }

    private fun stopNow(
        stopAt: List<(Book) -> Any?>? = defaultStopAt,
        book: Book,
    ): Boolean {
        return if (stopAt == null) {
            false
        } else {
            stopAt.firstOrNull {
                Property.isEmpty(it(book))
            } == null
        }
    }

    private fun onCompletion(
        index: Int,
        tag: String,
        book: Book,
        stopAt: List<(Book) -> Any?>? = defaultStopAt,
        onDone: (Book?) -> Unit) {
        for (i in index until clients.size) {
            val service = clients[i]
            if (stopNow(stopAt, book)) {
                break
            }
            if (service.preferenceGetter()) {
                service.client.lookup(tag, book) {
                    onCompletion(i + 1, tag, book, stopAt, onDone)
                }
                return
            }
        }
        onDone(if (book.title.isNotEmpty()) book else null)
    }

    private val defaultStopAt = listOf(
        Book::title.getter,
        Book::authors.getter,
        Book::imgUrl.getter,
    )

    /**
     * Lookup the given ISBN through our enabled lookup services.
     * Lookup services errors are logged and simply considered no match, we only take in a
     * match callback.
     * Returns an okHttp tag that can be given to cancelHttpRequests to cancel all pending lookups
     * for the given ISBN.
     * When [stopAt] is not null, we end the lookup once all provided getters return non
     * empty values.
     */
    fun lookup(
        isbn: String,
        stopAt: List<(Book) -> Any?>? = defaultStopAt,
        onDone: (book: Book?) -> Unit,
    ): String {
        val tag = nextTag()
        val book = app.repository.newBook(isbn)
        onCompletion(0, tag, book, stopAt, onDone)
        return tag
    }

}