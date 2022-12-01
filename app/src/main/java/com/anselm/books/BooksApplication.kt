package com.anselm.books

import android.app.Application
import android.net.Uri
import android.util.Log
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.Executors

// TODO - These should be moved to settings
private const val DISK_CACHE_SIZE = 10 * 204 * 1024L
private const val EXTERNAL_CACHE_DIRNAME = "picasso-cache"

class BooksApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy {
        BookRoomDatabase.getDatabase(this, applicationScope)
    }

    val repository by lazy {
        BookRepository(database.bookDao())
    }

    val picasso: Picasso by lazy {
        // Gets or creates the cache directory.
        val cacheDir = File(externalCacheDir, EXTERNAL_CACHE_DIRNAME)
        if ( ! cacheDir.exists() && ! cacheDir.mkdirs()) {
            Log.d(TAG, "Failed to create cache $cacheDir, not caching.")
        } else {
            Log.d(TAG, "Using $cacheDir for book cover image caching.")
        }
        // Amazon won't let us get images unless we claim to be Chrome.
        // This interceptor ensures all our requests have a Chrome-like User-Agent.
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val newRequest: Request = chain.request().newBuilder()
                    .addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36")
                    .build()
                Log.d(TAG, "Intercepted request for ${chain.request().url()}")
                chain.proceed(newRequest)
            }
            .cache(Cache(cacheDir, DISK_CACHE_SIZE))
            .build()
        // Finally build up the Picasso instance for this app.
        Picasso.Builder(applicationContext)
            .executor(Executors.newSingleThreadExecutor())
            .downloader(OkHttp3Downloader(client))
            .indicatorsEnabled(true)
            .listener { picasso: Picasso?, uri: Uri?, e: Exception? ->
                Log.e(TAG, "Picasso failed to load $uri", e)
            }.build()
    }
}