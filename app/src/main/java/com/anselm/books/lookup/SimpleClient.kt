package com.anselm.books.lookup

import android.util.Log
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.TAG
import com.anselm.books.database.Book
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.reflect.KMutableProperty0

class LookupCall(
    private val call: Call,
) {
    private var onResponseCallback: ((Response) -> Unit) ?= null
    private var onErrorCallback: ((e: IOException) -> Unit) ?= null

    fun onResponse(callback: (Response) -> Unit): LookupCall {
        this.onResponseCallback = callback
        return this
    }

    fun onError(callback: (e: IOException) -> Unit): LookupCall {
        this.onErrorCallback = callback
        return this
    }

    fun run(): Call {
        call.enqueue(object:Callback {
            override fun onFailure(call: Call, e: IOException) {
                onErrorCallback?.invoke(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    onResponseCallback?.invoke(response)
                }
            }
        })
        return call
    }
}

abstract class SimpleClient {
    private val client by lazy { app.okHttp }

    private fun isEmpty(propertyValue: Any?): Boolean {
        return when (propertyValue) {
            null -> { true }
            is String -> { propertyValue.isEmpty() }
            is List<*> -> { propertyValue.isEmpty() }
            else -> { true }
        }
    }

    protected fun hasAllProperties(
        book: Book,
        getters: List<(book: Book) -> Any?>,
    ) : Boolean {
        return getters.firstOrNull {
            isEmpty(it(book))
        } == null
    }

    protected fun setIfEmpty(prop: KMutableProperty0<*>, value: Any?) {
        val currentValue = prop.getter()
        if (isEmpty(currentValue) && ! isEmpty(value)) {
            @Suppress("UNCHECKED_CAST")
            (prop.setter as (Any) -> Unit)(value!!)
        }
    }

    protected fun setIfEmpty(vararg props: Pair<KMutableProperty0<*>, Any?>) {
        props.forEach { (prop, value) ->
            try {
                setIfEmpty(prop, value)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set ${prop.name} to $value (ignored).", e)
            }
        }
    }

    protected fun request(
        tag: String,
        url: String,
        useHead: Boolean = false): LookupCall {
        val req = Request.Builder()
            .tag(tag)
            .url(url)
        if (useHead) {
            req.head()
        }
        Log.d(TAG, "$tag: $url")
        return LookupCall(client.newCall(req.build()))
    }

    abstract fun lookup(
        tag: String,
        book: Book,
        onCompletion: () -> Unit,
    )
}