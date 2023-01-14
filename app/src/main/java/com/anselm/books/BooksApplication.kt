package com.anselm.books

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.util.DisplayMetrics
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
import okhttp3.Call
import java.io.File

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

    fun lookup(
        isbn: String,
        onError: (msg: String, e: Exception?) -> Unit,
        onBookOrig: (Book?) -> Unit,
    ): Call? {
        val onBook = { book: Book? -> lookupAmazonIfNeeded(isbn, book, onBookOrig) }
        when(prefs.getString("lookup_service", "Google")) {
            "Google" -> return glClient.lookup(isbn, onError, onBook)
            "OpenLibrary" -> return olClient.lookup(isbn, onError, onBook)
            "Worldcat" -> return oclcClient.lookup(isbn, onError, onBook)
            "Both" -> return lookupBoth(isbn, onError, onBook)
            else -> check(true)
        }
        return null
    }

    private fun lookupAmazonIfNeeded(isbn: String, book: Book?, onBook: (Book?) -> Unit) {
        // Preserves the ISBN in case welost it.
        if (book != null && book.isbn.isEmpty()) {
            book.isbn = isbn
        }
        // Fetches the cover from Amazon if none was found yet.
        if (book != null && book.imageFilename.isEmpty() && book.imgUrl.isEmpty() /* && prefs */) {
            amClient.cover(book, onBook)
        } else {
            onBook(book)
        }
    }

    private fun lookupBoth(
        isbn: String,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit,
    ): Call {
        return glClient.lookup(isbn, { _, _ ->
            olClient.lookup(isbn, onError, onBook)
        }, { glBook ->
            if (glBook == null) {
                olClient.lookup(isbn, onError, onBook)
            } else {
                olClient.lookup(isbn, { _, _ ->
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