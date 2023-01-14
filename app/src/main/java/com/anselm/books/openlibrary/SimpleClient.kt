package com.anselm.books.openlibrary

import com.anselm.books.database.Book
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

abstract class SimpleClient {
    private val client = OkHttpClient()

    /**
    * Runs the request and parses it as a json object, with three possible outcomes:
     * 1. All is good: onSuccess is invoked with the said json object,
     * 2. No errors, bt the request resulted in a 404, onBook is called with null to
     *    signal no-match
     * 3. Some error occurred, onError is invoked with at least an error message and may be an
     *    exception
     */
    fun runRequest(
        url: String,
        onError: (message: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit,
        onSuccess: ((JSONObject) -> Unit)? = null,
    ): Call {
        val req = Request.Builder().url(url).build()
        val call = client.newCall(req)

        call.enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("$url: get failed.", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        handleResponse(it, onError, onBook, onSuccess)
                    } catch (e: Exception) {
                        onError("$url: failed to handleResponse.", e)
                    }
                }
            }
        })
        return call
    }

    abstract fun handleResponse(
        resp: Response,
        onError: (message: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit,
        onSuccess: ((JSONObject) -> Unit)?
    )

    abstract fun lookup(
        isbn: String,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (matches: Book?) -> Unit,
    ): Call
}