package com.anselm.books

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.anselm.books.database.BookDao
import com.anselm.books.database.BookDatabase
import com.anselm.books.database.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.roundToInt

typealias ProgressReporter = (title: String?, count: Int, total: Int) -> Unit

class BooksApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob())

    val basedir by lazy {
        File(applicationContext?.filesDir, "books")
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

    val bookPrefs: BooksPreferences by lazy {
        BooksPreferences(prefs)
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
        val repository = BookRepository(database.bookDao())
        // Initializes the last location preference handling.
        LastLocationPreference(repository)
        repository
    }

    val importExport by lazy {
        ImportExport(repository, applicationContext?.contentResolver!!, basedir)
    }

    val imageRepository by lazy {
        ImageRepository(basedir)
    }

    val displayMetrics: DisplayMetrics by lazy { resources.displayMetrics }

    val lookupService by lazy {
        LookupService()
    }

    private val flushLock = ReentrantLock()
    private val flushCond = flushLock.newCondition()

    val okHttp by lazy {
        val result = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("User-Agent", Constants.USER_AGENT)
                        .header("Accept-Encoding", "identity")
                        .header("Accept", "*/*")
                        .build()
                )
            }.build()
        result.dispatcher.idleCallback = Runnable {
            Log.d(TAG, "okHttp: idle.")
            flushLock.withLock {
                flushCond.signalAll()
            }
        }
        result.dispatcher.maxRequestsPerHost = 10
        Log.d(TAG, "okHttp: maxReq=${result.dispatcher.maxRequests}, " +
                "maxReq/Host=${result.dispatcher.maxRequestsPerHost}")
        result
    }

    val httpQueuedCount: Int
        get() {
            return (okHttp.dispatcher.runningCalls().size + okHttp.dispatcher.queuedCalls().size)
        }

    fun flushOkHttp() {
        var pending: Int
        do {
            pending = httpQueuedCount
            Log.d(TAG, "okHttp: waiting for $pending calls.")
            if (pending > 0) {
                try {
                    flushLock.withLock {
                        flushCond.await(2000, TimeUnit.MILLISECONDS)
                    }
                } catch (_: InterruptedException) { }
            }
        } while (pending > 0)
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

    private data class Progress(
        val ruler: View,
        val text: TextView,
        val progress: ProgressBar,
        val cancelButton: ImageButton,
    )
    private var progress: Progress? = null
    fun enableProgressBar(
        rules: View,
        text: TextView,
        progressBar: ProgressBar,
        cancelButton: ImageButton
    ) {
        progress = Progress(rules, text, progressBar, cancelButton)
    }

    fun disableProgressBar() {
        progress = null
    }

    private fun progressVisibility(onOff: Boolean) {
        progress?.let {
            it.ruler.isVisible = onOff
            it.text.isVisible = onOff
            it.progress.isVisible = onOff
            it.cancelButton.isVisible = onOff       // Turned on via loadingDialog
            if ( ! onOff ) {
                it.text.text = ""
            }
        }
    }

    private val loadingTags = mutableMapOf<String, Boolean>()
    fun loading(text: String? = null, onOff: Boolean, tag: String = "global") {
        loadingTags[tag] = onOff
        val anyOn = loadingTags.toList().any(Pair<String, Boolean>::second)
        postOnUiThread {
            progressVisibility(anyOn)
            progress?.let {
                it.text.text = text ?: ""
                it.progress.isIndeterminate = true
            }
        }
    }

    fun loadingDialog(title: String, onCancel: (() -> Unit)? = null): ProgressReporter {
        progressVisibility(true)
        progress?.let {
            it.text.text = title
            if (onCancel != null) {
                it.cancelButton.isVisible = true
                it.cancelButton.setOnClickListener { onCancel() }
            } else {
                it.cancelButton.isVisible = false
            }
        }
        return { text: String?, count: Int, total: Int ->
            postOnUiThread {
                progressVisibility(true)
                progress?.let {
                    text.ifNotEmpty { s -> it.text.text = s  }
                    it.progress.isIndeterminate = false
                    if (total > 0) {
                        val percent = 100.0F * count.toFloat() / total.toFloat()
                        it.progress.progress = percent.roundToInt()
                    }
                }
            }
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

    /**
     * invoked by one only activity when it's pause.
     * A good time to perform some saves and what not.
     */
    fun onPause() {
        lookupService.saveStats()
    }

   companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var app: BooksApplication
            private set
    }

}