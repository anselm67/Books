package com.anselm.books.openlibrary

import com.anselm.books.database.Book
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.json.JSONTokener
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
        onSuccess: (JSONObject) -> Unit
    ): Call {
        val req = Request.Builder().url(url).build()
        val call = client.newCall(req)

        call.enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("$url: get failed.", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val tok = JSONTokener(resp.body!!.string())
                        val obj = tok.nextValue()
                        if (obj !is JSONObject) {
                            onError("$url: parse failed got a ${obj.javaClass.name}.", null)
                        } else {
                            onSuccess(obj)
                        }
                    } else {
                        if (resp.code == 404) {
                            //That's a no-match.
                            onBook(null)
                        } else {
                            // A real error.
                            onError("$url: HTTP Request failed, status $resp", null)
                        }
                    }
                }
            }
        })
        return call
    }

    abstract fun lookup(
        isbn: String,
        onError: (msg: String, e: Exception?) -> Unit,
        onBook: (matches: Book?) -> Unit,
    )
}