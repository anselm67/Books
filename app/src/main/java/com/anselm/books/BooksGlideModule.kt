package com.anselm.books

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.model.*
import com.bumptech.glide.load.model.stream.BaseGlideUrlLoader
import com.bumptech.glide.module.AppGlideModule
import java.io.InputStream


private class HeaderLoader(loader: ModelLoader<GlideUrl, InputStream>): BaseGlideUrlLoader<String>(loader) {

    override fun getUrl(model: String, width: Int, height: Int, options: Options?): String {
        return model
    }

    override fun getHeaders(model: String?, width: Int, height: Int, options: Options?): Headers? {
        // To fetch images from amazon, impersonate Chrome of face thw wrath of a 403
        return LazyHeaders.Builder()
            .addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36")
            .build()
    }

    override fun handles(model: String): Boolean {
        return model.startsWith("http://images.amazon.com/images/")
    }
}

private class Factory: ModelLoaderFactory<String, InputStream> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<String, InputStream> {
        val loader = multiFactory.build(
            GlideUrl::class.java,
            InputStream::class.java
        )
        return HeaderLoader(loader)
    }

    override fun teardown() {
        // Nothing to release.
    }
}

@GlideModule
class BooksGlideModule : AppGlideModule() {
    companion object {
        const val GLIDE_DEFAULT_CACHE_SIZE_MB = 50
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.append(String::class.java, InputStream::class.java, Factory())
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val prefs = BooksApplication.app.prefs
        builder.setDiskCache(
            InternalCacheDiskCacheFactory(
                context,
                prefs.getInt("glide_cache_size_mb", GLIDE_DEFAULT_CACHE_SIZE_MB).toLong()
            )
        )
    }
}