package com.anselm.books

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class BooksApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob())

    private val basedir by lazy {
        File(applicationContext?.filesDir, "import")
    }

    fun getCoverUri(filename: String): Uri {
        return File(basedir, filename).toUri()
    }

    fun toast(resId: Int) {
        toast(applicationContext.getString(resId))
    }

    fun toast(msg: String) {
        applicationScope.launch(Dispatchers.Main) {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate() {
        super.onCreate()
        app = this
    }

    val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(applicationContext)
    }

    val database by lazy {
        BookDatabase.getDatabase(this, applicationScope)
    }

    val repository by lazy {
        BookRepository(database.bookDao())
    }

    val importExport by lazy {
        ImportExport(repository, applicationContext?.contentResolver!!, basedir)
    }

    private var progressBarView: View? = null
    fun enableProgressBar(view: View) {
        progressBarView = view
    }

    fun disableProgressBar() {
        progressBarView = null
    }

    fun loading(onoff: Boolean) {
        applicationScope.launch(Dispatchers.Main) {
            progressBarView?.isVisible = onoff
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

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var app: BooksApplication
            private set
    }

}