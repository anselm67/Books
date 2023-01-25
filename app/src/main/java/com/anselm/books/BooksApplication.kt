package com.anselm.books

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.anselm.books.database.BookDao
import com.anselm.books.database.BookDatabase
import com.anselm.books.database.BookRepository
import com.anselm.books.databinding.ProgressBarDialogBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

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

    val okHttp by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("User-Agent", Constants.USER_AGENT)
                        .header("Accept-Encoding", "identity")
                        .header("Accept", "*/*")
                        .build()
                )
            }.build()
    }

    val lookupService by lazy {
        LookupService()
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


    fun loadingDialog(activity: Activity, onCancel: (() -> Unit)? = null): (Int) -> Unit {
        val binding = ProgressBarDialogBinding.inflate(activity.layoutInflater)
        val dialog = AlertDialog.Builder(activity)
            .setCancelable(false)
            .setView(binding.root)
            .create()
        if (onCancel != null) {
            binding.idCancelButton.isVisible = true
            binding.idCancelButton.setOnClickListener {
                onCancel()
                dialog.dismiss()
            }
        } else {
            binding.idCancelButton.isVisible = false
        }
        dialog.show()
        return { percent ->
            binding.idProgressBar.progress = percent
            if (percent >= 100) {
                dialog.dismiss()
            }
        }
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