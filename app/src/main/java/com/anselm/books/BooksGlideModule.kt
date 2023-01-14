package com.anselm.books

import android.content.Context
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.Headers
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.load.model.stream.BaseGlideUrlLoader
import com.bumptech.glide.module.AppGlideModule
import java.io.InputStream


private class HeaderLoader(
    delegate: ModelLoader<GlideUrl, InputStream>
): BaseGlideUrlLoader<Uri>(delegate) {

    override fun getUrl(model: Uri?, width: Int, height: Int, options: Options?): String {
        return model.toString()
    }

    override fun getHeaders(model: Uri?, width: Int, height: Int, options: Options?): Headers? {
        // To fetch images from amazon, impersonate Chrome of face thw wrath of a 403
        return LazyHeaders.Builder()
            .addHeader("User-Agent", Constants.USER_AGENT)
            .build()
    }

    override fun handles(model: Uri): Boolean {
        return model.toString().startsWith("http://images.amazon.com/images/")
    }

    class Factory : ModelLoaderFactory<Uri, InputStream> {
        override fun build(factory: MultiModelLoaderFactory): ModelLoader<Uri, InputStream> {
            return HeaderLoader(factory.build(GlideUrl::class.java, InputStream::class.java))
        }

        override fun teardown() {
            // Nothing to release.
        }
    }
}

@GlideModule
class BooksGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.prepend(Uri::class.java, InputStream::class.java, HeaderLoader.Factory())
    }

    /*override fun applyOptions(context: Context, builder: GlideBuilder) {
        // FIXME https://github.com/efemoney/maggg/blob/master/app/src/main/kotlin/com/efemoney/maggg/glide/MagggAppGlideModule.kt
        val prefs = BooksApplication.app.prefs
        val sizeMb = prefs.getInt("glide_cache_size_mb", GLIDE_DEFAULT_CACHE_SIZE_MB)
        builder.setDiskCache(
            InternalCacheDiskCacheFactory(
                context,
                1024L * 1024L * sizeMb.toLong()
            )
        )
    }*/

    override fun isManifestParsingEnabled(): Boolean = false
}