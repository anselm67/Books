package com.anselm.books

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.preference.PreferenceManager
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors

// TODO - These should be moved to settings
private const val PICASSO_DEFAULT_CACHE_SIZE_MB = 50
private const val EXTERNAL_CACHE_DIRNAME = "picasso-cache"

class BooksApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob())
    private var picassoCache: Cache? = null

    private val prefs by lazy {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        prefs.registerOnSharedPreferenceChangeListener { p, key: String ->
            when (key) {
                "picasso_cache_size_mb"
                    -> Log.d(TAG, "Picasso new cache size ${p.getInt(key, 0)}")
                "picasso_source_indicator"
                    -> {
                    Log.d(TAG, "Picasso indicator enabled ${p.getBoolean(key, false)}.")
                    picasso.setIndicatorsEnabled(p.getBoolean(key, false))
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
        ImportExport(repository, applicationContext?.contentResolver!!)
    }

    val picasso: Picasso by lazy {
        // Gets or creates the cache directory.
        val cacheDir = File(externalCacheDir, EXTERNAL_CACHE_DIRNAME)
        val cacheSize = prefs.getInt("picasso_cache_size_mb", PICASSO_DEFAULT_CACHE_SIZE_MB)
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            Log.d(TAG, "Picasso can't create cache $cacheDir, not caching.")
        } else {
            Log.d(TAG, "Picasso uses $cacheDir with $cacheSize Mb.")
        }
        picassoCache = Cache(cacheDir, cacheSize.toLong() * 1024L * 1024L)

        // Amazon won't let us get images unless we claim to be Chrome.
        // This interceptor ensures all our requests have a Chrome-like User-Agent.
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val newRequest: Request = chain.request().newBuilder()
                    .addHeader(
                        "User-Agent",
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36"
                    )
                    .build()
                Log.d(TAG, "Intercepted request for ${chain.request().url()}")
                chain.proceed(newRequest)
            }
            .cache(picassoCache)
            .build()
        // Finally build up the Picasso instance for this app.
        Picasso.Builder(applicationContext)
            .executor(Executors.newSingleThreadExecutor())
            .downloader(OkHttp3Downloader(client))
            .indicatorsEnabled(prefs.getBoolean("picasso_source_indicator", false))
            .listener { _: Picasso?, uri: Uri?, e: Exception? ->
                Log.e(TAG, "Picasso failed to load $uri", e)
            }.build()
    }

    fun clearPicassoCache() {
        // Clears the memory cache.
        picassoCache?.evictAll()
        // Clears the disk cache.
        val cacheDir = File(externalCacheDir, EXTERNAL_CACHE_DIRNAME)
        Files.walk(cacheDir.toPath())
            .filter { Files.isRegularFile(it) }
            .map { it.toFile() }
            .forEach { it.delete() }
    }
}