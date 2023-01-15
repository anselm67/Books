package com.anselm.books.lookup

import com.anselm.books.database.Book
import okhttp3.Response
import org.json.JSONObject
import org.json.JSONTokener

abstract class JsonClient: SimpleClient() {

    override fun handleResponse(
        resp: Response,
        onError: (message: String, e: Exception?) -> Unit,
        onBook: (Book?) -> Unit,
        onSuccess: ((JSONObject) -> Unit)?
    ) {
        val url = resp.request.url
        if (resp.isSuccessful) {
            val tok = JSONTokener(resp.body!!.string())
            val obj = tok.nextValue()
            if (obj !is JSONObject) {
                onError("$url: parse failed got a ${obj.javaClass.name}.", null)
            } else {
                onSuccess?.invoke(obj)
            }
        } else {
            if (resp.code == 404) {
                //That's a no-match.
                onBook(null)
            } else {
                // A real error.
                onError("$url: HTTP request failed, status $resp", null)
            }
        }
    }
}