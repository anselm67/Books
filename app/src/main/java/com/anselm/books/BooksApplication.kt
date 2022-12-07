package com.anselm.books

import android.app.Application
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// TODO - These should be moved to settings
private const val PICASSO_DEFAULT_CACHE_SIZE_MB = 50
private const val EXTERNAL_CACHE_DIRNAME = "picasso-cache"

class BooksApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob())

    private val basedir by lazy {
        File(applicationContext?.filesDir, "import")
    }

    private val prefs by lazy {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        prefs.registerOnSharedPreferenceChangeListener { p, key: String ->
            when (key) {
                "picasso_cache_size_mb"
                -> Log.d(TAG, "Picasso new cache size ${p.getInt(key, 0)}")
                "picasso_source_indicator"
                -> {
                    Log.d(TAG, "Picasso indicator enabled ${p.getBoolean(key, false)}.")
                    //picasso.setIndicatorsEnabled(p.getBoolean(key, false))
                }
            }
        }
        prefs
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

    val executor: ExecutorService = Executors.newFixedThreadPool(4)
}