package com.anselm.books

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.anselm.books.database.Book
import com.anselm.books.database.BookDao
import com.anselm.books.database.BookDatabase
import com.anselm.books.database.BookRepository
import com.anselm.books.openlibrary.AmazonImageClient
import com.anselm.books.openlibrary.GoogleBooksClient
import com.anselm.books.openlibrary.OclcClient
import com.anselm.books.openlibrary.OpenLibraryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class BooksApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob())

    private val basedir by lazy {
        File(applicationContext?.filesDir, "import")
    }

    fun toast(resId: Int) {
        toast(applicationContext.getString(resId))
    }

    fun toast(msg: String) {
        applicationScope.launch(Dispatchers.Main) {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
        }
    }

    fun postOnUiThread(block: () ->Unit) {
        applicationScope.launch(Dispatchers.Main) { block() }
    }

    override fun onCreate() {
        super.onCreate()
        app = this
    }

    val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(applicationContext)
    }

    val defaultSortOrder: Int get() {
        return when (prefs.getString("sort_order", "DateAdded")) {
            "DateAdded" -> BookDao.SortByDateAdded
            "Alphabetical" -> BookDao.SortByTitle
            else -> BookDao.SortByDateAdded
        }
    }

    val database by lazy {
        BookDatabase.getDatabase(this)
    }

    val repository by lazy {
        BookRepository(database.bookDao())
    }

    val importExport by lazy {
        ImportExport(repository, applicationContext?.contentResolver!!, basedir)
    }

    val imageRepository by lazy {
        ImageRepository(applicationContext, basedir)
    }

    val displayMetrics: DisplayMetrics by lazy { resources.displayMetrics }

    val okHttp by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("User-Agent", Constants.USER_AGENT)
                        .build()
                )
            }.build()
    }

    fun cancelHttpRequests(tag: String): Int {
        var count = 0
        okHttp.dispatcher.runningCalls().map { call ->
            if (call.request().tag() == tag) {
                call.cancel()
                count++
            }
        }
        okHttp.dispatcher.queuedCalls().map { call ->
            if (call.request().tag() == tag) {
                call.cancel()
                count++
            }
        }
        Log.d(TAG, "okHttp: canceled $count calls.")
        return count
    }

    private val bookMerger by lazy {
        BookMerger()
    }

    private var progressBarView: View? = null
    fun enableProgressBar(view: View) {
        progressBarView = view
    }

    fun disableProgressBar() {
        progressBarView = null
    }

    private val loadingTags = mutableMapOf<String, Boolean>()
    fun loading(onOff: Boolean, tag: String = "global" ) {
        loadingTags[tag] = onOff
        val anyOn = loadingTags.toList().any(Pair<String, Boolean>::second)
        applicationScope.launch(Dispatchers.Main) {
            progressBarView?.isVisible = anyOn
        }
    }

    private var titleSetter: ((String) -> Unit) ? = null
    fun enableTitle(titleSetter: (String) -> Unit) {
        this.titleSetter = titleSetter
    }

    var title: String? = null
        set (value) {
            titleSetter?.let {
                it(value ?: "")
            }
            field = value
        }

    private val olClient = OpenLibraryClient()
    private val glClient = GoogleBooksClient()
    private val oclcClient = OclcClient()
    private val amClient = AmazonImageClient()

    private val requestIdCounter = AtomicInteger(1)
    private fun nextTag(): String {
        return "lookup-${requestIdCounter.incrementAndGet()}"
    }

    fun lookup(
        isbn: String,
        onError: (msg: String, e: Exception?) -> Unit,
        onBookOrig: (Book?) -> Unit,
    ): String {
        val tag = nextTag()
        val onBook = { book: Book? -> lookupAmazonIfNeeded(tag, isbn, book, onBookOrig) }
        val serviceId = prefs.getString("lookup_service", "Google")
        when(serviceId) {
            "Google" -> glClient.lookup(tag, isbn, onError, onBook)
            "OpenLibrary" -> olClient.lookup(tag, isbn, onError, onBook)
            "Worldcat" -> oclcClient.lookup(tag, isbn, onError, onBook)
            "Both" -> lookupBoth(tag, isbn, onError, onBook)
            else -> check(true) { "Unsupported lookup service identifier $serviceId"}
        }
        return tag
    }

    private fun lookupAmazonIfNeeded(
        tag: String,
        isbn: String,
        book: Book?,
        onBook: (Book?) -> Unit
    ) {
        // Preserves the ISBN in case we lost it.
        if (book != null && book.isbn.isEmpty()) {
            book.isbn = isbn
        }
        // Fetches the cover from Amazon if none was found yet.
        if (book != null && book.imageFilename.isEmpty() && book.imgUrl.isEmpty() /* && prefs */) {
            amClient.cover(tag, book, onBook)
        } else {
            onBook(book)
        }
    }

    private fun lookupBoth(
        tag: String,
        isbn: String,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit,
    ) {
        glClient.lookup(tag, isbn, { _, _ ->
            olClient.lookup(tag, isbn, onError, onBook)
        }, { glBook ->
            if (glBook == null) {
                olClient.lookup(tag, isbn, onError, onBook)
            } else {
                olClient.lookup(tag, isbn, { _, _ ->
                    // If OpenLibrary fails when GoogleBooks succeeded, use GoogleBooks.
                    onBook(glBook)
                }, { olBook ->
                    // Now we have two versions of the book, merge them.
                    onBook(if (olBook != null) {
                        app.bookMerger.merge(glBook, olBook)
                    } else {
                        glBook
                    })
                })
            }
        })
    }

    private fun digit(c: Char): Int {
        return c.digitToInt()
    }

    fun isValidEAN13(isbn: String): Boolean {
        // Quick checks: empty is fine.
        if (isbn.isEmpty()) {
            return true
        } else if (isbn.length != 13) {
            return false
        }
        // Computes the expected checksum / last digit.
        val sum1 = arrayListOf(0, 2, 4, 6, 8, 10).sumOf { it -> digit(isbn[it]) }
        val sum2 = 3 * arrayListOf(1, 3, 5, 7, 9, 11).sumOf { it -> digit(isbn[it]) }
        val checksum = (sum1 + sum2) % 10
        val expected = if (checksum == 0) '0' else ('0' + 10 - checksum)
        return expected == isbn[12]
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var app: BooksApplication
            private set
    }

}