package com.anselm.books.openlibrary

import android.util.Log
import com.anselm.books.TAG
import com.anselm.books.database.Book
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.headersContentLength
import java.io.IOException

class AmazonImageClient {
    private val client = OkHttpClient()

    private fun setCoverIfExists(
        tag: String,
        book: Book,
        url: String,
        onBook: (Book) -> Unit,
    ): Call {
        val req = Request.Builder()
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .tag(tag)
            .url(url)
            .head()
            .build()
        val call = client.newCall(req)
        call.enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "$url: HTTP Request failed, onFailure (ignored).", e)
                onBook(book)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful && response.headersContentLength() > 50) {
                        book.imgUrl = url
                    }
                    onBook(book)
                }
            }
        })
        return call
    }

    fun cover(tag: String, book: Book, onBook: (Book) -> Unit): Call? {
        if (book.isbn.length <= 3) {
            return null
        }
        val key = book.isbn.substring(3)
        val amazonCoverUrl = "http://images.amazon.com/images/P/$key.01Z.jpg"

        try {
            return setCoverIfExists(tag, book, amazonCoverUrl, onBook)
        } catch (e: Exception) {
            Log.e(TAG, "$amazonCoverUrl: HTTP Request failed (ignored).", e)
            onBook(book)
        }
        return null
    }
}